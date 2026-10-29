import {EditorState} from '@codemirror/state';
import {EditorView} from '@codemirror/view';
import {setValidationErrors, validationExtension} from './codemirror-error-ext';

describe("validation error markers", () => {
  test("ignore errors that are not located at a line", () => {
    const view = new EditorView({
      state: EditorState.create({doc: "<ead>\n</ead>", extensions: [validationExtension]}),
      parent: document.body,
    });
    // An XML error at line 2, and a field validation error, which has no line
    const errors = [
      {line: 2, pos: 1, error: "bad element"},
      {field: "Titel", kind: "unexpected", error: "Unexpected field 'Titel'"},
    ] as any;
    expect(() => view.dispatch({effects: setValidationErrors.of(errors)})).not.toThrow();
    expect(view.dom.querySelectorAll(".line-error").length).toBe(1);
    view.destroy();
  });
});
