EHRI Portal API - Version 1 - Experimental
==========================================

The EHRI portal has an experimental API, intended for searching and retrieving a subset
of EHRI data in structured JSON format. While it is intended that the scope of the API will
broaden in future, it is intended to prioritise convenience over semantic precision, providing
a somewhat simplified view relative to that offered by the HTML site.

**Note: since it is currently in testing the API currently requires a bearer access token to be provided
in the Authorization header: contact WP13.3 for more details.**

At present, information is only available for three types of item:

1. Countries (type: `Country`)
2. Institutions (type: `Repository`)
3. Archival descriptions (type: `DocumentaryUnit`)


The base API URL is `https://portal.ehri-project.eu/api/v1`.

Three _actions_ are available:

1. Global search at `/search`:
   Intended for a simple-text query of all country report, institution, and archival description
   information in the portal. Optionally, the search can be limited to items of specific types.
2. Retrieving item info by ID at `/{ID}`:
   If item's IDs are known in advance (or determined via a search), information about them can
   be fetched individually.   
3. Item-scoped search at `{ID}/search`:
   Intended for searching via simple text query within the "scope" of a particular item, retrieving
   matching child items. For example, a country can be searched for specific repositories, and
   repositories and archival descriptions for, respectively, top-level and sub-level descriptions.

The format of returned data conforms to the `http://jsonapi.org/` specification and has content-type
`application/vnd.api+json`.

Examples (using the `curl` commandline tool):

## Searching repositories for text "USHMM"

    curl -H Accept:application/vnd.api+json \
         "https://portal.ehri-project.eu/api/v1/search?type=Repository&q=USHMM"

Example result:

    {
      "data": [
        {
          "id": "us-005578",
          "type": "Repository",
          "attributes": {
            "name": "United States Holocaust Memorial Museum",
            "address": {
              "streetAddress": "100 Raoul Wallenberg Place, S.W.",
              "city": "Washington",
              "region": "District of Columbia",
              "postalCode": "DC 20024",
              "countryCode": "US",
              "email": [
                "archives@ushmm.org"
              ],
              "telephone": [
                "202 488 0400"
              ],
              "fax": [
                "202-479-9726"
              ],
              "url": [
                "http://www.ushmm.org/"
              ]
            }
          },
          "relationships": {
            "country": {
              "data": {
                "id": "us",
                "type": "Country"
              }
            }
          },
          "links": {
            "self": "http://localhost:9000/api/v1/us-005578",
            "search": "http://localhost:9000/api/v1/us-005578/search",
            "country": "http://localhost:9000/api/v1/us"
          }
        },
           
        ... MORE RESULTS ...

      ],
      "links": {
        "first": "http://localhost:9000/api/v1/search?q=USHMM",
        "last": "http://localhost:9000/api/v1/search?q=USHMM"
      }
    }

The first item in the `data` array is the description of USHMM, with its attributes containing
the institution's name and address. The `relationships` object shows other queryable items that
USHMM is related to, such as the country report record for the United States. Outside the top-level
`data` object, the top-level `links` object contains URLs relevant to pagination for this search
result set. In the example, the first and last page URLs are the same, which indicates that there
is only one page of results.


## Retrieving data about the United States Holocaust Memorial Museum, using its ID

    curl -H Accept:application/vnd.api+json \
         "https://portal.ehri-project.eu/api/v1/us-005578"

Example result:

    {
      "data": {
        "id": "us-005578",
        "type": "Repository",
        "attributes": {
          "name": "United States Holocaust Memorial Museum",
          "address": {
            "streetAddress": "100 Raoul Wallenberg Place, S.W.",
            "city": "Washington",
            "region": "District of Columbia",
            "postalCode": "DC 20024",
            "countryCode": "US",
            "email": [
              "archives@ushmm.org"
            ],
            "telephone": [
              "202 488 0400"
            ],
            "fax": [
              "202-479-9726"
            ],
            "url": [
              "http://www.ushmm.org/"
            ]
          }
        },
        "relationships": {
          "country": {
            "data": {
              "id": "us",
              "type": "Country"
            }
          }
        },
        "links": {
          "self": "http://localhost:9000/api/v1/us-005578",
          "search": "http://localhost:9000/api/v1/us-005578/search",
          "country": "http://localhost:9000/api/v1/us"
        }
      }
    }

In this case we get the same object that was the first item in the data array for
the search.

## Searching archival descriptions held by USHMM for "Oral History'

    curl -H Accept:application/vnd.api+json \
         "https://portal.ehri-project.eu/api/v1/us-005578/search?q=Oral%20History"

Example result:

    {
      "data": [
        {
          "id": "us-005578-501539",
          "type": "DocumentaryUnit",
          "attributes": {
            "localId": "501539",
            "alternateIds": [],
            "descriptions": [
              {
                "localId": "eng",
                "languageCode": "eng",
                "name": "501539",
                "parallelFormsOfName": [],
                "extentAndMedium": "1 folder",
                "acquisition": "Accession number: 1997.A.0230",
                "scopeAndContent": "Contains\"Struggle & Sacrifice, The Oral History of Leon & Sara Fajgenbaum,\" copyrighted1995, an English-language edited oral history based on the Spanish-language oral testimony given by Leon and Sara Fajgenbaum; a translated oral history transcript from the Spanish language oral testimony of Leon and Sara Fajgenbaum; and a copyprint of Noam Lupu's grandparents and uncle taken in 1948 in Ulm (Germany)."
              }
            ]
          },
          "relationships": {
            "holder": {
              "data": {
                "id": "us-005578",
                "type": "Repository"
              }
            },
            "parent": null
          },
          "links": {
            "self": "http://localhost:9000/api/v1/us-005578-501539",
            "search": "http://localhost:9000/api/v1/us-005578-501539/search",
            "holder": "http://localhost:9000/api/v1/us-005578"
          }
        },

        ... MORE RESULTS ...

      ],
      "links": {
        "first": "http://localhost:9000/api/v1/us-005578/search?q=Oral+History",
        "last": "http://localhost:9000/api/v1/us-005578/search?q=Oral+History&page=15",
        "next": "http://localhost:9000/api/v1/us-005578/search?q=Oral+History&page=2"
      },
      "included": [
        {
          "id": "us-005578",
          "type": "Repository",
          "attributes": {
            "name": "United States Holocaust Memorial Museum",
            "address": {
              "streetAddress": "100 Raoul Wallenberg Place, S.W.",
              "city": "Washington",
              "region": "District of Columbia",
              "postalCode": "DC 20024",
              "countryCode": "US",
              "email": [
                "archives@ushmm.org"
              ],
              "telephone": [
                "202 488 0400"
              ],
              "fax": [
                "202-479-9726"
              ],
              "url": [
                "http://www.ushmm.org/"
              ]
            }
          },
          "relationships": {
            "country": {
              "data": {
                "id": "us",
                "type": "Country"
              }
            }
          },
          "links": {
            "self": "http://localhost:9000/api/v1/us-005578",
            "search": "http://localhost:9000/api/v1/us-005578/search",
            "country": "http://localhost:9000/api/v1/us"
          }
        }
      ]
    }


In this case the top-level `data` section contains an array of documentary units items matching
our "Oral History" search. This time the top-level `links` object contains the URL to the `next`
page, and also the `last` page of the results, in example page 15. We also have a top-level
`included` object, which contains the data of items deemed relavant to the main data, which here
contains the USHMM repository record within whose scope we're searching. 
