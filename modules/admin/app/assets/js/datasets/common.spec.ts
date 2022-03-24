
import {
  decodeTsv,
  encodeTsv,
} from "./common";

test("encodeTsv", () => {
  let data = [["header1", "header2"], ["a", "b"], ["c", "d"]];
  let tsv = encodeTsv(data, 2);
  expect(tsv).toBe("header1\theader2\na\tb\nc\td");
});

test("decodeTsv", () => {
  let tsv = "header1\theader2\na\tb\nc\td\n";
  let data = decodeTsv(tsv, 2);
  expect(data).toStrictEqual([["header1", "header2"], ["a", "b"], ["c", "d"]])
});
