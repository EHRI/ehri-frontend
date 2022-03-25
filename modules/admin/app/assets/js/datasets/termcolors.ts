
const RED = "\x1b[31m";
const GREEN = "\x1b[32m";
const RESET = "\x1b[0m";

function green(s: string): string {
    return GREEN + s + RESET;
}

function red(s: string): string {
  return RED + s + RESET;
}

export {green, red};
