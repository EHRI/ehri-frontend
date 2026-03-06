/**
 * Configuration for CodeMirror 6 error and success markers.
 *
 * The validator starts with a NULL array of {FileValidationError} instances, indicating
 * that it hasn't yet been checked.
 */

import {Decoration, EditorView, gutter, GutterMarker, WidgetType} from '@codemirror/view';
import {RangeSet, StateEffect, StateField} from '@codemirror/state';
import {FileValidationError} from "./types";

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

class SuccessMarker extends GutterMarker {
  toDOM() {
    let span = document.createElement("span");
    span.className = "validation-success";
    span.innerHTML = '<i class="fa fa-check-circle" style="color: #28a745; margin-left: 3px;"></i>';
    span.title = "Document is valid";
    return span;
  }
}

class ErrorWidget extends WidgetType {
  constructor(readonly error: string) { super(); }
  toDOM() {
    let div = document.createElement("div");
    div.className = "cm-error-widget";
    div.textContent = this.error;
    return div;
  }
}

export const setValidationErrors = StateEffect.define<FileValidationError[] | null>();
export const toggleErrorWidget = StateEffect.define<number | null>();

const errorState = StateField.define<{
  errors: FileValidationError[] | null,
  activeLine: number | null,
}>({
  create(): { errors: FileValidationError[] | null, activeLine: number | null } {
    return {
      errors: null,
      activeLine: null,
    };
  },
  update(value, tr) {
    let { errors, activeLine } = value;
    if (tr.docChanged) {
      activeLine = null;
    }

    for (let e of tr.effects) {
      if (e.is(setValidationErrors)) {
        errors = e.value;
        activeLine = null;
      }
      if (e.is(toggleErrorWidget)) {
        activeLine = activeLine === e.value ? null : e.value;
      }
    }
    return { errors, activeLine };
  },
  provide: f => [
    gutter({
      class: "validation-errors",
      markers: (v: EditorView) => {
        // Use the provided 'f' to get the state safely
        const field = v.state.field(f, false);
        if (!field) return RangeSet.empty;

        const { errors} = field;

        if (errors === null) {
          return RangeSet.empty;
        } else if (errors.length === 0) {
          return RangeSet.of([new SuccessMarker().range(v.state.doc.line(1).from)]);
        } else {
          const markers = errors
            .filter(e => e.line > 0 && e.line <= v.state.doc.lines)
            .map(e => new ErrorMarker(e.error).range(v.state.doc.line(e.line).from))
            .sort((a, b) => a.from - b.from); // Ensure sorted order

          return RangeSet.of(markers);
        }
      },
      domEventHandlers: {
        click(view, block) {
          const line = view.state.doc.lineAt(block.from);
          view.dispatch({
            effects: toggleErrorWidget.of(line.number)
          });
          return true;
        }
      }
    }),

    EditorView.decorations.compute([f], state => {
      const field = state.field(f, false);
      if (!field) {
        return RangeSet.empty;
      }
      const { errors, activeLine } = field;
      if (errors === null) {
        return RangeSet.empty;
      }

      const decos = [];
      for (let e of errors) {
        if (e.line <= 0 || e.line > state.doc.lines) {
          continue;
        }
        const line = state.doc.line(e.line);
        decos.push(Decoration.line({ class: "line-error" }).range(line.from));
        if (activeLine === e.line) {
          decos.push(Decoration.widget({
            widget: new ErrorWidget(e.error),
            block: false
          }).range(line.from));
        }
      }
      // Sort is required for RangeSet.of/Decoration.set
      let sorted = decos.sort((a, b) => a.from - b.from);
      return Decoration.set(sorted);
    })
  ]
});

// Styling
const errorTheme = EditorView.baseTheme({
  ".validation-errors": { width: "25px" },
  ".validation-error, .validation-success": {
    display: "block",
    cursor: "pointer"
  },
  ".line-error": { backgroundColor: "rgba(255,197,199,0.3)" },
  ".cm-error-widget": {
    color: "#822",
    backgroundColor: "rgba(255,197,199,0.6)",
    padding: "4px 10px",
    fontSize: "0.85em",
    fontFamily: "sans-serif",
    borderLeft: "3px solid #822"
  }
});

export const validationExtension = [errorState, errorTheme];
