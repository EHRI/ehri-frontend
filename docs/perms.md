# Permissions

## Overview

The EHRI admin interface has a flexible permission system that is designed to allow several types of privileges:

 - global (super-admin) privileges
 - global priviliges restricted to a particular content-type
 - scoped privileges that apply to items within the "permission scope" of other items (e.g. archival units held by repositories)
 - item-level privileges

Each of these privilege types can be assigned either directly to an individual user or to a group to which they belong.

## Justification

Some of the different scenarios this system was intended to support are outlined in [this document](FIX LINK).

## Permission Sets from the Client Perspective

Permission sets are bundles of data that describe a user's permissions for either a *content type* or a specific entity. All together, there are three different types of permission sets:

 - Global Permission Sets
 - Scoped Permission Sets
 - Item Permission Sets

When it loads a particular page the application (the client) has to gather the user's permissions that are relevant in a given context. For all intents and purposes the client only needs to know the user's permissions for **display purposes** so we don't allow users to perform actions (like updating a particular item) for which they don't have the right privileges. The actual permissions will be **enforced** on the server side, but for a good user experience the client also needs to be aware of them.

Both **global** and **scoped** permissions have the same structure. The only difference is that when gathering permissions in the context of a given *item*, that item's *scope* (typically it's parent item, if any) comes into play. The data structure, in JSON, looks like:

```json
[
    {
        "bob": {
            "documentaryUnit": [
                "create",
                "update",
                "delete"
            ],
            "repository": [
                "update"
            ]
        }
    }, {
        "bobs-group": {
            "country": [
                "create"
            ]
        }
    }
]
```

The entire permission set above pertains to the user "bob", who belongs to a group "bobs-group". Bob has *create*, *update*, and *delete* permissions for the content type "documentaryUnit" and *update* permissions for the "repository" content type. In addition, Bob **inherits** the *create* permission for the content type "country" from a group he belongs to.

If fetched in the context of a particular **scope** the data will have the same structure, but Bob may have additional permissions if, for example, the item is a repository and Bob has permissions to create additional documentaryUnit items within **just** that repository.

One thing to note about this structure is that item's within the top level list (user -> contentType/Permission map) is semantically a **pair** rather than a map since it should only have one key/value (and others should be ignored.) A map was used to make parsing simpler in JSON.

Item permissions, by contrast, have a simpler structure:

```json
[
    {
        "bob": [
            "create",
            "update",
            "delete"
        ]
    }, {
        "bobs-group": [
            "annotate"
        ]
    }
]
```

Because we presumably know the content type of the item we're fetching permissions for, the data we get back only needs to tell us:

 - what permissions we have
 - who they're assigned to (a user themselves, or inherited from a given group.)

## Permissions in a request context

Typically, for each request the current user's global permissions are fetched from the server. For pages that pertain to a particular item, such as detail pages, we instead fetch the *scoped* global permissions and any item-level permissions that may apply (note: these requests are cached with a short expiry time in order to reduce the number of server round-trips needed.) Once the global, scoped, or item-level permission JSON is retrieved it is parsed into either a `GlobalPermissionSet` or `ItemPermissionSet` instance, which provide methods to verify if a user has a given permission (for instance, **create** on content type **documentaryUnit**) and to retrieve individual `Permission` instances, which allow determining if the given permission was granted directly to the user or inherited from a group to which they belong.


