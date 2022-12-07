import {AccessPointType, AccessPointTypeData, ConfigType} from "../types";


export default class AccessPointEditorApi {
  constructor(service: object, config: ConfigType) {
    console.log("AccessPointEditorApi mock constructor called");
  }

  label(type: AccessPointType): string {
    return "Subject Access Point Mock Test";
  }

  itemUrl(targetType: string, id: string): string {
    return `/item/${targetType}/${id}`;
  }

  getAccessPoints(id: string, did: string): Promise<AccessPointTypeData[]> {
    return Promise.resolve([
      {
        type: "subject",
        data: [
          {
            accessPoint: {
              id: "test-subject-ap",
              isA: "AccessPoint",
              accessPointType: "subject",
              name: "Test Subject",
              description: ""
            },
            link: {
              isA: "Link",
              id: "test-subject-link",
              linkType: "associative",
              description: ""
            },
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
