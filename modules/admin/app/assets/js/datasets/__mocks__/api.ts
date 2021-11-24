import {FileList, HarvestConfig} from "../types";

export class DatasetManagerApi {
  constructor(service: object, repoId: string) {
    console.log("DatasetManagerApi mock constructor called");
  }

  listFiles(ds: string, stage: string, prefix: string, after?: string): Promise<FileList> {
    return Promise.resolve<FileList>({
          files: [
            {
              classifier: "test",
              key: "hello.xml",
              size: 100,
              lastModified: '2020-01-01T10:00:00',
            },
            {
              classifier: "test",
              key: "goodbye.xml",
              size: 100,
              lastModified: '2020-01-01T10:00:00',
            }
          ],
          truncated: false,
        }
    )
  }

  getHarvestConfig(ds: string): Promise<HarvestConfig | null> {
    return Promise.resolve(null);
  }
}
