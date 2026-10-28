
// The worker registers a global message listener, so we load it once
// and drive it by dispatching message events with fetch mocked.

function mockResponse(props: object): object {
  return {
    ok: false,
    status: 200,
    statusText: "",
    redirected: false,
    text: () => Promise.resolve(""),
    ...props,
  };
}

function flush(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, 0));
}

describe("dataset worker", () => {
  let postMessage: jest.Mock;

  beforeAll(() => {
    require("./worker.js");
  });

  beforeEach(() => {
    postMessage = jest.fn();
    window.postMessage = postMessage;
  });

  async function send(type: string, response: object | Error, extra: object = {}): Promise<void> {
    global.fetch = jest.fn(() => response instanceof Error
        ? Promise.reject(response)
        : Promise.resolve(response)) as unknown as typeof fetch;
    window.dispatchEvent(new MessageEvent("message", {data: {type, url: "/test", ...extra}}));
    await flush();
  }

  test("reports a login failure for a convert preview", async () => {
    await send("convert-preview", mockResponse({status: 401, text: () => Promise.resolve("authentication failed")}));
    expect(postMessage).toHaveBeenCalledWith({error: {error: "not logged in, or session has expired"}, done: true});
  });

  test("reports a permission failure for a convert preview", async () => {
    await send("convert-preview", mockResponse({status: 403, text: () => Promise.resolve("<html>Denied</html>")}));
    expect(postMessage).toHaveBeenCalledWith({error: {error: "permission denied"}, done: true});
  });

  test("passes through JSON conversion errors", async () => {
    let err = {line: 2, pos: 1, error: "bad mapping"};
    await send("convert-preview", mockResponse({status: 400, text: () => Promise.resolve(JSON.stringify(err))}));
    expect(postMessage).toHaveBeenCalledWith({error: err, done: true});
  });

  test("does not show an error page as preview data", async () => {
    await send("preview", mockResponse({status: 403, text: () => Promise.resolve("<html>Denied</html>")}));
    expect(postMessage).toHaveBeenCalledTimes(1);
    expect(postMessage).toHaveBeenCalledWith({error: {error: "permission denied"}, done: true});
  });

  test("marks app preview requests as AJAX, so auth failures are not redirects", async () => {
    await send("preview", mockResponse({status: 401}), {ajax: true});
    expect((global.fetch as jest.Mock).mock.calls[0][1].headers).toEqual({"X-Requested-With": "XMLHttpRequest"});
    expect(postMessage).toHaveBeenCalledWith({error: {error: "not logged in, or session has expired"}, done: true});
  });

  test("does not add headers to storage preview requests", async () => {
    await send("preview", mockResponse({status: 403}));
    expect((global.fetch as jest.Mock).mock.calls[0][1].headers).toEqual({});
  });

  test("reports network errors", async () => {
    await send("preview", new Error("Failed to fetch"));
    expect(postMessage).toHaveBeenCalledWith({error: {error: "Failed to fetch"}, done: true});
  });

  test("ignores aborted requests", async () => {
    let abort = new Error("aborted");
    abort.name = "AbortError";
    await send("preview", abort);
    expect(postMessage).not.toHaveBeenCalled();
  });
});
