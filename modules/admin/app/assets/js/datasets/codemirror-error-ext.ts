import {Decoration, EditorView, gutter, GutterMarker, WidgetType} from '@codemirror/view';
import {RangeSet, StateEffect, StateField} from '@codemirror/state';
import {XmlValidationError} from "./types";

// --- Effects & State ---

const setErrorsEffect = StateEffect.define<XmlValidationError[]>()

// Gutter markers
class ErrorMarker extends GutterMarker {
  constructor(readonly error: string) { super(); }
  toDOM() {
    let span = document.createElement("span");
    span.className = "validation-error";
    span.innerHTML = '<i class="fa fa-exclamation-circle" style="color: #822; margin-left: 3px;"></i>';
    span.title = this.error;
    return span;
  }
}

class ErrorWidget extends WidgetType {
  constructor(readonly error: string) { super(); }
  toDOM() {
    let div = document.createElement("div");
    div.style.cssText = `color: #822; background-color: rgba(255,197,199,0.44); padding: 2px 5px;`;
    div.textContent = this.error;
    return div;
  }
}

// Effects to update the state
export const setValidationErrors = StateEffect.define<XmlValidationError[]>();
export const toggleErrorWidget = StateEffect.define<number | null>(); // line number

const errorState = StateField.define<{ errors: XmlValidationError[], activeLine: number | null }>({
  create() { return { errors: [], activeLine: null }; },
  update(value, tr) {
    let { errors, activeLine } = value;
    for (let e of tr.effects) {
      if (e.is(setValidationErrors)) {
        errors = e.value;
        activeLine = null; // Reset expanded widget on new errors
      }
      if (e.is(toggleErrorWidget)) {
        activeLine = activeLine === e.value ? null : e.value;
      }
    }
    return { errors, activeLine };
  },
  provide: f => [
    // Provide Gutter Markers
    gutter({
      class: "validation-errors",
      // Use the reference 'f' instead of 'errorState' inside its own definition
      markers: (v: EditorView) => {
        const { errors } = v.state.field(f);
        return RangeSet.of(
          errors.map((e: XmlValidationError) => ({
            from: v.state.doc.line(e.line).from,
            to: v.state.doc.line(e.line).from + e.pos,
            value: new ErrorMarker(e.error)
          })),
          true
        );
      },
      domEventHandlers: {
        click(view, block) {
          const line = view.state.doc.lineAt(block.from);
          view.dispatch({ effects: toggleErrorWidget.of(line.number) });
          return true;
        }
      }
    }),
    // Provide Line Widgets & Background Highlighting
    // Line decorations and widgets
    EditorView.decorations.compute([f], state => {
      const { errors, activeLine } = state.field(f);
      const decos = [];

      for (let e of errors) {
        // CM6 lines are 1-based. Ensure e.line exists in doc to avoid crashes
        if (e.line > state.doc.lines) continue;

        const line = state.doc.line(e.line);

        // Add the background color class to the line
        decos.push(Decoration.line({ class: "line-error" }).range(line.from));

        // Add the widget if toggled
        if (activeLine === e.line) {
          decos.push(Decoration.widget({
            widget: new ErrorWidget(e.error),
            block: true
          }).range(line.from));
        }
      }
      // RangeSet must be sorted by position
      return Decoration.set(decos, true);
    })
  ]
});

/**
 * Styling
 */
const errorTheme = EditorView.baseTheme({
  ".validation-errors": {
    width: "20px"
  },
  ".validation-error": {
    color: "#822",
    paddingLeft: "3px",
    cursor: "pointer"
  },
  ".line-error": {
    backgroundColor: "rgba(255,197,199,0.44)"
  },
  ".cm-error-widget": {
    color: "#822",
    backgroundColor: "rgba(255,197,199,0.44)",
    padding: "2px 10px",
    fontSize: "0.9em",
    fontFamily: "sans-serif"
  }
});

// Final Exported Extension
export const validationExtension = [errorState, errorTheme];
