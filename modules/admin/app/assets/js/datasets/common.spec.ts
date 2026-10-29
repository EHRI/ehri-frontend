
import {
  decodeTsv,
  encodeTsv,
} from "./common";

test("encodeTsv", () => {
  let data = [["header1", "header2"], ["a", "b"], ["c", "d"]];
  let tsv = encodeTsv(data, 2);
  expect(tsv).toBe("header1\theader2\na\tb\nc\td");
});

test("encodeTsv with quotes", () => {
  let data = [["header1", "header2"], ["a", "\"b\""], ["c", "d with \"quotes\""]];
  let tsv = encodeTsv(data, 2);
  expect(tsv).toBe("header1\theader2\na\t\"b\"\nc\td with \"quotes\"");
});

test("encodeTsv with tabs", () => {
  let data = [["header1", "header2"], ["a", "b"], ["c", "d\te"]];
  let tsv = encodeTsv(data, 2);
  expect(tsv).toBe("header1\theader2\na\tb\nc\td\te");
});

test("decodeTsv", () => {
  let tsv = "header1\theader2\na\tb\nc\td\n";
  let data = decodeTsv(tsv, 2);
  expect(data).toStrictEqual([["header1", "header2"], ["a", "b"], ["c", "d"]])
});

test("decodeTsv with quotes", () => {
  let tsv = "header1\theader2\na\t\"b\"\nc\td with \"quotes\"\n";
  let data = decodeTsv(tsv, 2);
  expect(data).toStrictEqual([["header1", "header2"], ["a", "\"b\""], ["c", "d with \"quotes\""]]);
});

test("decodeTsv(quoted) resolves a quoted field with an embedded newline to one row", () => {
  let tsv = "joined\n\"line1\nline2\"\n";
  let data = decodeTsv(tsv, 1, true);
  expect(data).toStrictEqual([["joined"], ["line1\nline2"]]);
});

test("encodeTsv(quoted) quotes a value containing an embedded newline", () => {
  let data = [["joined"], ["line1\nline2"]];
  let tsv = encodeTsv(data, 1, true);
  expect(tsv).toBe("joined\n\"line1\nline2\"");
});
