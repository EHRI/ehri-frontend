/**
 * Access point data definitions: the server-side equivilant to these
 * definitions are in `AccessPoints.scala`.
 */

export type Ok = {ok: true}

export type LinkType = "identity" | "associative" | "family" | "hierarchical" | "temporal" | "copy";

export interface Link {
  id: string
  isA: string
  linkType: LinkType
  linkField?: string
  description?: string
}

export type AccessPointType = "creator" | "person" | "family" | "corporateBody" | "subject" | "place" | "genre";

export interface AccessPoint {
  id: string
  isA: string
  accessPointType: AccessPointType
  name: string
  description?: string
}

export interface Target {
  id: string
  type: string
}

export interface LinkItem {
  accessPoint: AccessPoint
  link?: Link
  target?: Target
}

export interface AccessPointTypeData {
  type: AccessPointType
  data: LinkItem[]
}

export interface ItemAccessPoints {
  id?: string // description ID
  data: AccessPointTypeData[]
}

export interface FilterResult {
  // See: filterItems in Portal.scala
  numPages: number
  page: number
  items: FilterHit[]
}

export interface FilterHit {
  id: string
  did: string
  name: string
  type: string
  parent?: string
  gid: number
}

export interface ConfigType {
  id: string
  did: string
  linkType: string
  holderIds: string[]
  typeFilters: Record<AccessPointType, string[]>
  labels: Record<AccessPointType, string>
}

export interface CreationData {
  text: string
  description: string
  targetId: string
}


