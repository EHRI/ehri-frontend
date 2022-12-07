import {
  AccessPoint,
  AccessPointType,
  AccessPointTypeData,
  ConfigType,
  CreationData,
  Link,
  LinkType,
  Ok
} from "../types";

const testAccessPoint = {
  id: "test-subject-ap",
  isA: "AccessPoint",
  accessPointType: "subject" as AccessPointType,
  name: "Test Subject",
  description: ""
}

const testLink = {
  isA: "Link",
  id: "test-subject-link",
  linkType: "associative" as LinkType,
  description: ""
}

export default class AccessPointEditorApi {
  constructor(service: object, config: ConfigType) {}

  label(type: AccessPointType): string {
    return "Subject Access Point Mock Test";
  }

  itemUrl(targetType: string, id: string): string {
    return `/item/${targetType}/${id}`;
  }

  deleteAccessPoint(id: string, did: string, apId: string): Promise<Ok> {
    return Promise.resolve({ok: true});
  }

  deleteLinkAndAccessPoint(id: string, did: string, apId: string, linkId: string): Promise<Ok> {
    return Promise.resolve({ok: true});
  }

  createLink(id: string, apId: string, data: object): Promise<Link> {
    return Promise.resolve(testLink);
  }

  createAccessPoint(id: string, did: string, data: object): Promise<AccessPoint> {
    return Promise.resolve(testAccessPoint);
  }

  async createAccessPoints(id: string, descId: string, type: AccessPointType, data: CreationData[]): Promise<Ok> {
    return Promise.resolve({ok: true});
  }

  getAccessPoints(id: string, did: string): Promise<AccessPointTypeData[]> {
    return Promise.resolve([
      {
        type: "subject" as AccessPointType,
        data: [
          {
            accessPoint: testAccessPoint,
            link: testLink,
            target: {
              id: "test-subject-target",
              type: "CvocConcept"
            }
          }
        ]
      }
    ]);
  }
}
