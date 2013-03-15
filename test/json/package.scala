package object json {
  val documentaryUnitTestJson = """
    {
      "id" : "wiener-library-GB 1556 WL 760",
      "data" : {
        "creators" : [ "Jüdische Volkspartei" ],
        "unitDates" : [ "1933" ],
        "corporateBodies" : [ "Jüdische Volkspartei" ],
        "subjects" : [ ],
        "name" : "  Appeal regarding leadership",
        "nameAccess" : [ ],
        "places" : [ "Berlin", "Europe", "Germany", "Western Europe" ],
        "identifier" : "GB 1556 WL 760"
      },
      "type" : "documentaryUnit",
      "relationships" : {
        "describes" : [ {
          "id" : "7786576c-9aee-4a83-9463-ef747cdef6d3",
          "data" : {
            "systemOfArrangement" : "",
            "physicalCharacteristics" : "",
            "locationOfOriginals" : "",
            "conditionsOfReproduction" : "Copies can be made for personal use. Permission must be sought for publication.",
            "languagesOfMaterial" : [ "en" ],
            "conditionsOfAccess" : "Open",
            "rules" : "",
            "publisher" : "Wiener Library",
            "creationDate" : "",
            "accruals" : "",
            "archivalHistory" : "",
            "acquisition" : "Jewish Central Information Office",
            "title" : "Jüdische Volkspartei:  Appeal regarding leadership",
            "languageCode" : "en",
            "depthOfDescription" : 0,
            "extentAndMedium" : "1 item",
            "levelOfDescription" : "fonds",
            "scopeAndContent" : "Appeal to the council of the Jewish community, Berlin, concerning the leadership of the Jewish organisation, Jüdische Volkspartei signed by Kozower, lawyer, 23 Sep 1933.",
            "identifier" : "GB 1556 WL 760",
            "appraisal" : "",
            "publicationDate" : "",
            "locationOfCopies" : ""
          },
          "type" : "documentDescription",
          "relationships" : {
      "hasDate" : [ {
        "id" : "28f011c9-f913-4942-a11c-89fbbe8d0eb9",
        "data" : {
          "startDate" : "1933-01-01",
          "endDate" : "1959-01-01"
        },
        "type" : "datePeriod",
        "relationships" : {
        }
      } ]
          }
        } ],
        "heldBy" : [ {
          "id" : "wiener-library",
          "data" : {
            "publicationStatus" : "Draft",
            "name" : "Wiener Library",
            "identifier" : "wiener-library"
          },
          "type" : "repository",
          "relationships" : {
            "describes" : [ {
              "id" : "5379607d-0b92-48a0-8309-c6b9e209a0dd",
              "data" : {
                "otherFormsOfName" : [ "The Wiener Library" ],
                "history" : "The Wiener Library is in London",
                "languageCode" : "en"
              },
              "type" : "repositoryDescription",
              "relationships" : {
                "hasAddress" : [ {
                  "id" : "134494ac-ff89-457c-b1d0-b0d88bc60836",
                  "data" : {
                    "addressName" : "primary"
                  },
                  "type" : "address",
                  "relationships" : {
                  }
                } ]
              }
            } ]
          }
        } ],
        "lifecycleEvent" : [ {
          "id" : "d65dedc3-5b7e-4688-89f6-f7198f165f28",
          "data" : {
            "timestamp" : "2013-02-14T13:02:52.447Z",
            "identifier" : "c83f104f-9487-4c2f-8bcf-e183316ee1b8",
            "logMessage" : "Imported from command-line"
          },
          "type" : "systemEvent",
          "relationships" : {
            "hasActioner" : [ {
              "id" : "mike",
              "data" : {
                "name" : "mike",
                "identifier" : "mike"
              },
              "type" : "userProfile",
              "relationships" : {
                "belongsTo" : [ {
                  "id" : "admin",
                  "data" : {
                    "name" : "Administrators",
                    "identifier" : "admin"
                  },
                  "type" : "group",
                  "relationships" : {
                  }
                } ]
              }
            } ]
          }
        } ]
      }
    }
  """

  val actorTestJson =
    """
      {
        "id" : "another-test-authority",
        "data" : {
          "publicationStatus" : "Draft",
          "name" : "Test Authority 2",
          "identifier" : "another-test-authority"
        },
        "type" : "historicalAgent",
        "relationships" : {
          "describes" : [ {
            "id" : "cdb5d0aa-2100-49f9-8481-2b7b278825fa",
            "data" : {
              "otherFormsOfName" : [ "Another name for Test Authority 2" ],
              "history" : "Testing",
              "languages" : [ "en" ],
              "scripts" : [ "Latn" ],
              "languageCode" : "en",
              "name" : "Test Authority 2",
              "typeOfEntity" : "corporateBody",
              "parallelFormsOfName" : [ "Parellel name for Test Authority 2" ],
              "datesOfExistence" : "1900-2000",
              "history": "Some history"
            },
            "type" : "historicalAgentDescription",
            "relationships" : {
            }
          } ],
          "lifecycleEvent" : [ {
            "id" : "e2e15165-8fd1-42b1-8512-319177267050",
            "data" : {
              "timestamp" : "2013-03-08T17:19:39.224+01:00",
              "identifier" : "5a47b976-91b1-42e7-9342-ba3175560ee3",
              "logMessage" : "Updating item (historicalAgent): 'another-test-authority'"
            },
            "type" : "systemEvent",
            "relationships" : {
              "hasActioner" : [ {
                "id" : "mike",
                "data" : {
                  "languages" : [ "en" ],
                  "location" : "London",
                  "name" : "Mike Bryant",
                  "about" : "Testing the system",
                  "identifier" : "mike"
                },
                "type" : "userProfile",
                "relationships" : {
                  "belongsTo" : [ {
                    "id" : "admin",
                    "data" : {
                      "description" : "The all-powerful system administrators who never accidentally delete stuff.",
                      "name" : "Administrators",
                      "identifier" : "admin"
                    },
                    "type" : "group",
                    "relationships" : {
                    }
                  } ]
                }
              } ]
            }
          } ],
          "access" : []
        }
      }
    """

  val repoTestJson =
    """
      {
        "id" : "wiener-library",
        "data" : {
          "publicationStatus" : "Draft",
          "name" : "Wiener Library",
          "identifier" : "wiener-library"
        },
        "type" : "repository",
        "relationships" : {
          "describes" : [ {
            "id" : "9c5eadd2-6c57-4467-ba02-cd4b2c49ffa6",
            "data" : {
              "languageCode" : "en",
              "name" : "Wiener Library"
            },
            "type" : "repositoryDescription",
            "relationships" : {
              "hasAddress" : [ {
                "id" : "d9569bef-a939-4565-83ae-a52e57e6b222",
                "data" : {
                  "streetAddress" : "29 Russell Square",
                  "name" : "Primary",
                  "city" : "London"
                },
                "type" : "address",
                "relationships" : {
                }
              } ]
            }
          } ],
          "lifecycleEvent" : [ {
            "id" : "936e8380-1747-46b1-9dc9-cd64db493798",
            "data" : {
              "timestamp" : "2013-03-08T19:00:54.731+01:00",
              "identifier" : "babe0ea4-8c66-4607-b545-199b29976bba",
              "logMessage" : "Added annotation"
            },
            "type" : "systemEvent",
            "relationships" : {
              "hasActioner" : [ {
                "id" : "mike",
                "data" : {
                  "languages" : [ "en" ],
                  "location" : "London",
                  "name" : "Mike Bryant",
                  "about" : "Testing the system",
                  "identifier" : "mike"
                },
                "type" : "userProfile",
                "relationships" : {
                  "belongsTo" : [ {
                    "id" : "admin",
                    "data" : {
                      "description" : "The all-powerful system administrators who never accidentally delete stuff.",
                      "name" : "Administrators",
                      "identifier" : "admin"
                    },
                    "type" : "group",
                    "relationships" : {
                    }
                  } ]
                }
              } ]
            }
          } ]
        }
      }
  """

  val conceptTestJson =
    """
      {
        "id" : "bac412d0-e58b-4090-a419-7f007706a4ab",
        "data" : {
          "identifier" : "http://ehri01.dans.knaw.nl/tematres/vocab/?tema=671"
        },
        "type" : "cvocConcept",
        "relationships" : {
          "inCvoc" : [ {
            "id" : "ehri-skos",
            "data" : {
              "description" : "SKOS version of the WP18 thesaurus",
              "name" : "EHRI Skos",
              "identifier" : "ehri-skos"
            },
            "type" : "cvocVocabulary",
            "relationships" : {}
          } ],
          "describes" : [ {
            "id" : "812552fc-7169-40c1-a428-5f2af5727e36",
            "data" : {
              "languageCode" : "en",
              "prefLabel" : "Art culture science religion"
            },
            "type" : "cvocConceptDescription",
            "relationships" : {
            }
          } ],
          "lifecycleEvent" : [ {
            "id" : "7cf4212b-011f-4b46-b85a-41ec5fda2cb4",
            "data" : {
              "timestamp" : "2013-02-01T17:06:38.992Z",
              "__USER_ID_CACHE__" : "mike",
              "identifier" : "10997557-ec46-41fa-a2b2-69e3fb00f11f",
              "logMessage" : "Imported from command-line"
            },
            "type" : "systemEvent",
            "relationships" : {
              "hasActioner" : [ {
                "id" : "mike",
                "data" : {
                  "languages" : [ "en" ],
                  "location" : "London",
                  "name" : "Mike Bryant",
                  "about" : "Testing the system",
                  "identifier" : "mike"
                },
                "type" : "userProfile",
                "relationships" : {
                  "belongsTo" : [ {
                    "id" : "admin",
                    "data" : {
                      "description" : "The all-powerful system administrators who never accidentally delete stuff.",
                      "name" : "Administrators",
                      "identifier" : "admin"
                    },
                    "type" : "group",
                    "relationships" : {
                    }
                  } ]
                }
              } ]
            }
          } ]
        }
      }
    """

  val vocabTestJson =
    """
      {
        "id" : "ehri-skos",
        "data" : {
          "description" : "Preliminary SKOS version of the WP18 thesaurus",
          "name" : "EHRI Skos",
          "identifier" : "ehri-skos"
        },
        "type" : "cvocVocabulary",
        "relationships" : {}
      }
    """

  val userProfileTestJson =
    """
      {
        "id" : "mike",
        "data" : {
          "languages" : [ "en" ],
          "location" : "London",
          "name" : "Mike Bryant",
          "about" : "Testing the system",
          "identifier" : "mike"
        },
        "type" : "userProfile",
        "relationships" : {
          "lifecycleEvent" : [ {
            "id" : "8d852619-339c-447c-9934-ed21d8584d7a",
            "data" : {
              "timestamp" : "2013-03-08T17:39:16.216+01:00",
              "identifier" : "c21c4f71-a6ab-49c8-b450-97bf8ea39687",
              "logMessage" : "Updating item (userProfile): 'mike'"
            },
            "type" : "systemEvent",
            "relationships" : {
              "hasActioner" : [ {
                "id" : "mike",
                "data" : {
                  "languages" : [ "en" ],
                  "location" : "London",
                  "name" : "Mike Bryant",
                  "about" : "Testing the system",
                  "identifier" : "mike"
                },
                "type" : "userProfile",
                "relationships" : {
                  "belongsTo" : [ {
                    "id" : "admin",
                    "data" : {
                      "description" : "The all-powerful system administrators who never accidentally delete stuff.",
                      "name" : "Administrators",
                      "identifier" : "admin"
                    },
                    "type" : "group",
                    "relationships" : {
                    }
                  } ]
                }
              } ]
            }
          } ],
          "belongsTo" : [ {
            "id" : "admin",
            "data" : {
              "description" : "The all-powerful system administrators who never accidentally delete stuff.",
              "name" : "Administrators",
              "identifier" : "admin"
            },
            "type" : "group",
            "relationships" : {}
          } ]
        }
      }
    """

  val groupTestJson =
    """
      {
        "id" : "restricted-access",
        "data" : {
          "description" : "Users/Groups who can view restricted material.",
          "name" : "Restricted Access",
          "identifier" : "restricted-access"
        },
        "type" : "group",
        "relationships" : {
          "lifecycleEvent" : [ {
            "id" : "98114aa9-e5f7-48bb-8deb-8543633b539a",
            "data" : {
              "timestamp" : "2013-03-10T18:37:17.300+01:00",
              "identifier" : "81805f42-5772-4ae9-a494-521f82523fc1",
              "logMessage" : "Added userProfile to group"
            },
            "type" : "systemEvent",
            "relationships" : {
              "hasActioner" : [ {
                "id" : "mike",
                "data" : {
                  "languages" : [ "en" ],
                  "location" : "London",
                  "name" : "Mike Bryant",
                  "about" : "Testing the system",
                  "identifier" : "mike"
                },
                "type" : "userProfile",
                "relationships" : {
                  "belongsTo" : [ {
                    "id" : "admin",
                    "data" : {
                      "description" : "The all-powerful system administrators who never accidentally delete stuff.",
                      "name" : "Administrators",
                      "identifier" : "admin"
                    },
                    "type" : "group",
                    "relationships" : {
                    }
                  } ]
                }
              } ]
            }
          } ]
        }
      }
    """

  val annotationTestJson =
    """
      {
        "id" : "f21d0367-125f-4bbb-9f77-d79e4b723472",
        "data" : {
          "body" : "Test Annotation",
          "annotationType" : "comment",
          "identifier" : "a318bfed-be6d-447f-ae7a-e10529836f32"
        },
        "type" : "annotation",
        "relationships" : {}
      }
    """
}
