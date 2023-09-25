# Admin messages

# General main page parts
pages.portal=Veřejná stránka
pages.home=Domů
pages.search=Hledat
pages.more=Více
pages.admin=Správcovská stránka

admin.home=Domů
admin.recentActivity=Nedávná aktivita
admin.recentlyViewed=Nedávno zobrazeno
admin.home.title=Vítejte v administrativním rozhraní EHRI
admin.metrics=Data Metrics
admin.metrics.show=Data Overview Graphs
admin.goBack=Jít zpět

facet.group=Skupina
facet.admin=Správci
facet.targetType=Link Target Types
facet.linkType=Link Type
facet.linkField=Link Field

#
# Errors
#
errors.badRegexPattern=Regular expression syntax error
errors.invalidValue=Invalid value selected

#
#
# Friendly names for content types
contentTypes=Content types
contentTypes.Group=Skupiny
contentTypes.UserProfile=Uživatelé
contentTypes.Repository=Instituce
contentTypes.HistoricalAgent=Historical Agents
contentTypes.DocumentaryUnit=Documentary Units
contentTypes.Action=User Events
contentTypes.CvocVocabulary=Vocabularies
contentTypes.AuthoritativeSet=Authority Sets
contentTypes.CvocConcept=Terms
contentTypes.Annotation=Annotations
contentTypes.Country=Country Reports
contentTypes.Link=Links
contentTypes.SystemEvent=History
contentTypes.VirtualUnit=Virtual Collections
contentTypes.Guides=Guides

#
# Permission Types
#
permissionTypes.create=Vytvořit
permissionTypes.update=Aktualizovat
permissionTypes.delete=Smazat
permissionTypes.grant=Grant
permissionTypes.annotate=Anotovat
permissionTypes.owner=Owner
permissionTypes.promote=Propagovat

#
# Annotations
#
annotation.bodyText=Text
annotation.annotationType=Typ
annotation.emptyBodyText=[Missing Annotation Text]
annotation.creator=Created By
annotation.target=Target



#
# Linking
#
link.search=Search Links
link.linkTo=Links to Other Items
link.linkTo.item=Create Annotation Link
link.linkTo.copy=Create Copy Link
link.linkTo.submit=Create Relationship
link.targetType=Typ položky
link.source=Zdroj
link.destination=Target
link.items=Související položky
link.itemLink=Relationship
link.creator=Relationship Created By
link.description=Description of Relationship
link.description.description=Some text that describes how these items are related.
link.create=Link to another item
link.create.item=Link ''{0}'' to ''{1}''
link.update=Edit Relationship
link.update.submit=Odeslat
link.create.to.DocumentaryUnit=Link to an Archival Description
link.create.to.Repository=Link to an Institution
link.create.to.HistoricalAgent=Link to an Authority File
link.create.to.VirtualUnit=Link to Virtual Item
link.create.to.CvocConcept=Link to a Vocabulary Term
link.delete=Delete Relationship
link.dates=Dates of Relationship
link.type=Type of Relationship
link.associative=Associative
link.hierarchical=Hierarchical
link.identity=Identity
link.family=Familial
link.temporal=Temporal
link.copy=Copy
link.copy.create=This link will denote copies between items and/or institutions.
link.copy.preset.copyRepositoryToOriginalRepository={0} holds copies of Holocaust-relevant archives from {1}
link.copy.preset.copyCollectionToOriginalCollection="{0}" was copied from "{1}"
link.copy.preset.copyCollectionToOriginalRepository=The collection "{0}" was copied from {1}
link.copy.preset.copyRepositoryToOriginalCollection={0} holds a copy of "{1}"
link.creation=Vytvoření
link.existence=Existence
link.startDate=Start Date
link.endDate=End Date
link.field=Related Field
link.locationOfOriginals=Location of Originals
link.locationOfCopies=Location of Copies
link.relatedUnitsOfDescription=Related Units of Description

#
# Generic actions
#
item.actions=Actions
item.identifier=Identifikátor
item.history=Item History
item.history.item=Change history for item: {0}
item.annotate.submit=Annotate Item
item.export=Exportovat
item.export.json=Exportovat JSON
item.export.rdf.xml=RDF/XML
item.export.rdf.ttl=TTL
item.delete=Smazat položku
item.delete.info=Are you sure you want to delete item: "{0}"?  This action cannot be undone.
item.delete.submit=Smazat položku
item.delete.confirmation=Item successfully deleted
item.delete.childrenFirst=This item has {0} child {0,choice,1#item|1<items}. Deleting a parent item requires deleting lower level items first.
item.deleteChildren=Delete Contents
item.deleteChildren.all=Include lower level items
item.deleteChildren.all.description=Checking this box confirms you want to delete all items, including those that contain lower-level items of their own.
item.deleteChildren.answer=Confirm phrase
item.deleteChildren.answer.description=
item.deleteChildren.badConfirmation=Confirmation phrase did not match
item.deleteChildren.noChildren=No contents found.
item.deleteChildren.confirm=delete {0} {0,choice,1#item|1<items}
item.deleteChildren.info=You are about to delete {0} {0,choice,1#item|1<items} and this action cannot be undone.
item.deleteChildren.submit=Delete {0} {0,choice,1#Item|1<Items}
item.deleteChildren.confirmPhrase=Type the following confirmation phrase: "{0}":
item.deleteChildren.confirmation={0} {0,choice,1#item|1<items} successfully deleted
item.create=Create Item
item.create.submit=Vytvořit
item.create.confirmation=Item successfully created
item.create.when=Created {0}
item.update=Upravit položku
item.update.submit=Update
item.update.confirmation=Item successfully updated
item.update.when=Updated {0}
item.promote=Promote Item Publicly
item.promote.submit=Promote Item
item.promote.confirmation=Item promoted
item.demote=Remove Item Promotion
item.demote.submit=Remove Promotion
item.demote.confirmation=Item promotion removed
item.noItemsFound=No items found
item.field.add=Add Field
item.field.remove=Remove Field
item.rename=Change Identifier: {0}
item.rename.warning=Changing an item''s identifier will change it's URL and the URLs of any child items. The current identifier is:
item.rename.submit=Submit
item.rename.identifier=New Identifier
item.rename.confirmation=Item successfully renamed; new 301 URL redirects: {0}
item.rename.title=Change identifier
item.rename.collisions.error=ID collisions detected
item.rename.collisions=ID Collisions
item.rename.collisions.details=Identifiers must be unique at the same level of description. Changing the identifier of this item would create conflicts with the following items:
item.rename.subject=This item

#
# Cypher queries
#
cypherQuery.list=Database Queries
cypherQuery.create=New query
cypherQuery.create.submit=Create Query
cypherQuery.name=Name
cypherQuery.name.description=A distinct name for this query
cypherQuery.query=Cypher
cypherQuery.query.description=The Cypher code for this query
cypherQuery.description=Description
cypherQuery.description.description=A description of this query
cypherQuery.public=Public
cypherQuery.public.description=Non-admin users can run this query
cypherQuery.execute=Execute
cypherQuery.execute.description=Execute this query
cypherQuery.download=Download
cypherQuery.download.description=Execute and download query results
cypherQuery.results=Results
cypherQuery.console=Cypher console
cypherQuery.delete=Delete Query
cypherQuery.delete.submit=Delete
cypherQuery.delete.description=Delete query (this action cannot be undone)
cypherQuery.update=Edit Query
cypherQuery.update.submit=Update Query
cypherQuery.update.description=Edit query
cypherQuery.mutatingClauses=Field must not contain mutating clauses
cypherQuery.mutatingClauses.error=Query contains mutating clause: {0}
cypherQuery.test=Test Query
cypherQuery.sort.name=Name
cypherQuery.sort.name.title=Display results sorted by title
cypherQuery.sort.created=Created
cypherQuery.sort.created.title=Display results order of creation
cypherQuery.sort.updated=Updated
cypherQuery.sort.updated.title=Display results most recently updated first
cypherQuery.checkAll=Query Validation
cypherQuery.check.details=Details
download.format.html=View as HTML

# Provenance
forms.logMessage=Log Message
forms.logMessagePlaceholder=Tell people what you''re doing (optional, 400 characters max)...

# What we say when a logical item has no descriptions
describedEntity.description=Description {0}
describedEntity.editDescription=Edit Description
describedEntity.deleteDescription=Delete Description
describedEntity.deleteDescription.submit=Delete
describedEntity.deleteDescription.logMessage=Deleted description ''{0}''
describedEntity.deleteDescription.info=Are you sure you want to delete item description: "{0}"?  This action cannot be undone.
describedEntity.deleteDescription.lastError=You cannot delete the last description from an item. Delete the item instead.
describedEntity.noData=No information given
describedEntity.notGiven=Not Given
describedEntity.createDescription=Add Description
describedEntity.createDescription.item=Add Description to {0}
describedEntity.createDescription.submit=Add Description
describedEntity.createDescription.logMessage=Added Description
describedEntity.updateDescription=Update Description
describedEntity.updateDescription.submit=Update Description
describedEntity.updateDescription.logMessage=Updated description ''{0}''
describedEntity.manageAccessPoints=Manage Access Points
describedEntity.creationProcess.importWarning=Warning! This description is marked as beingcreated via an import process. This means that changes to the data may be overwritten ifthe import data is subsequently updated.

#
# Metrics
#
metrics.languageOfMaterial=Archival Descriptions - Language of Material
metrics.holdingRepository=Archival Descriptions - Holding Repository
metrics.repositoryCountries=Repositories by Country
metrics.agentTypes=Authority Files by Entity Type
metrics.restrictedMaterial=Restricted Material

#
# Moved pages
#
admin.utils.movedItems=Add ID Redirects
admin.utils.movedItems.description=Create permanent redirects for items that have had their IDs changed.
admin.utils.movedItems.submit=Redirect item IDs
admin.utils.movedItems.csv=Select the old ID to new ID CSV file...
admin.utils.movedItems.added=The following URLs will be redirected:
admin.utils.movedItems.added.none=No new redirects were created

#
# Renaming items
#
admin.utils.renameItems=Rename Item Local Identifiers
admin.utils.renameItems.description=Change item local identifiers, regenerate global IDs, and create permanent redirects from old ID to new ID.
admin.utils.renameItems.submit=Rename Items
admin.utils.renameItems.pathPrefix=Specify the path prefix before the item's id (comma-separated if there are multiple).
admin.utils.renameItems.csv=Select the ID to new local identifier CSV file...

#
# Re-parenting items
#
admin.utils.reparentItems=Reparent Items
admin.utils.reparentItems.description=Move items to a new parent item, regenerate global IDs, and create permanent redirects from old ID to new ID.
admin.utils.reparentItems.submit=Reparent Items
admin.utils.reparentItems.csv=Select the ID to new parent ID CSV file...

#
# ID regeneration
#
admin.utils.regenerateIds=Regenerate Item IDs
admin.utils.regenerateIds.description=Items that require ID regeneration.
admin.utils.regenerateIds.scope=Item scope
admin.utils.regenerateIds.type=Item type
admin.utils.regenerateIds.submit=Regenerate IDs
admin.utils.regenerateIds.scan=Scan Items
admin.utils.regenerateIds.scanning=Scanning items with stale IDs...
admin.utils.regenerateIds.noIdsFound=No IDs requiring regeneration found.
admin.utils.regenerateIds.chooseOne=Choose either scope or type, you cannot choose both.
admin.utils.regenerateIds.tolerant=Ignore Collisions
admin.utils.regenerateIds.collisions=Note: if you recieve collision errors these can sometimes be remedied by opting to ignore collisions first, then making a second attempt.
admin.utils.regenerateIds.tolerant.description=Skip items where regenerating an ID would conflict with an existing item ID.

#
# Find and replace
#
admin.utils.findReplace=Find and Replace
admin.utils.findReplace.found=Items found: {0}
admin.utils.findReplace.notFound=No items found
admin.utils.findReplace.done=Items updated: {0}
admin.utils.findReplace.find=Find
admin.utils.findReplace.replace={0,choice,0#Replace {0} Values|1#Replace 1 Value|1<Replace {0,number,integer} Values}
admin.utils.findReplace.description=Find and replace text across a particular property of an item type.Note: you can only change up to 100 items at a time.
admin.utils.findReplace.warning=Don''t do this unless you're sure you know what you''re doing, since it is a sharp tool.
admin.utils.findReplace.type=Parent item type
admin.utils.findReplace.type.description=The top-level content item, e.g. a Repository.
admin.utils.findReplace.subtype=Dependent item type
admin.utils.findReplace.subtype.description=The property-holding item type, e.g. a Repository description.
admin.utils.findReplace.property=Property
admin.utils.findReplace.property.description=The property name.
admin.utils.findReplace.from=Text to find
admin.utils.findReplace.from.description=The current text to find.
admin.utils.findReplace.to=Replacement text
admin.utils.findReplace.to.description=The replacement text.
admin.utils.findReplace.logMessage=Log message
admin.utils.findReplace.logMessage.description=Describe why you are making this change.

#
# Batch operations
#
admin.utils.batchDelete=Batch Delete Items
admin.utils.batchDelete.description=Delete multiple items in one go.
admin.utils.batchDelete.warning=Be very careful with this since it is NOT reversable.
admin.utils.batchDelete.submit=Delete Items
admin.utils.batchDelete.scope=Item scope
admin.utils.batchDelete.scope.description=If items share a common scope you specify it here. Otherwise omit
admin.utils.batchDelete.version=Create pre-delete version
admin.utils.batchDelete.version.description=Create a version of each item before deleting it
admin.utils.batchDelete.commit=Commit changes
admin.utils.batchDelete.commit.description=Actually commit deletions to the database
admin.utils.batchDelete.logMessage=Log message
admin.utils.batchDelete.logMessage.description=Describe why you are making this change.
admin.utils.batchDelete.ids=IDs (one item ID per line)
admin.utils.batchDelete.ids.description=Specify IDs of items to be deleted with one item per line
admin.utils.batchDelete.done=Items deleted: {0}

#
# Redirects
#
admin.utils.redirect=Create Page Redirect
admin.utils.redirect.description=Create a permanent (301) redirect from one page URL to another.
admin.utils.redirect.from=From Path
admin.utils.redirect.from.description=The path (starting with ''/'') from which should be redirected.
admin.utils.redirect.to=To Path
admin.utils.redirect.to.description=The path (starting with ''/'') to which the redirect should point.
admin.utils.redirect.submit=Submit
admin.utils.redirect.badPathError=To/From paths must be local (e.g. starting with ''/'')
admin.utils.redirect.done=Redirect successfully created

# Admin search index
search.index.update=Refresh Search Index
search.index.update.for=Refresh search index for {0}
search.index.clear.all=Clear Entire Index First
search.index.clear.types=Clear Each Type First
search.index.types=Types to Update
search.index.types.selectAll=Select All

# Ead Validation
admin.utils.validate=EAD Validation
admin.utils.validate.file.name=File
admin.utils.validate.file.line=Line
admin.utils.validate.file.pos=Column
admin.utils.validate.file.details=Details
admin.utils.validate.file.errors=Errors
admin.utils.validate.file.errors.number={0,choice,0#No errors|1#1 error|1<{0,number,integer} errors}
admin.utils.validate.okay=No Errors
admin.utils.validate.files=Select EAD Files
admin.utils.validate.submit=Validate



# Users- and Group-related i18n messages

accessor.groups.manage=Manage Groups
accessor.groups.manage.submit=Submit
accessor.notInAnyGroups=This user does not belong to any groups.
accessor.currentGroups=Current Groups
accessor.itemCannotBeAddedToAdditionalGroups=This {0} cannot be added to additional groups
accessor.addNewGroup=Add New Group
accessor.groups.add=Add
accessor.groups.add.item=Add {0} to group {1}?
accessor.groups.add.submit=Submit
accessor.groups.remove=Remove
accessor.groups.remove.item=Remove {0} from group {1}?
accessor.groups.remove.submit=Remove

#
# Users
#
userProfile.staff=Staff
userProfile.staff.description=Whether the user can access admin pages
userProfile.staff.true=Staff
userProfile.staff.false=Non-staff
userProfile.verified=Verified
userProfile.verified.description=Whether the user''s email has been verified
userProfile.email.verify=User''s email address {0} has not been verified and this will prevent them accessing admin pages. Verify it now?
userProfile.unverified=Unverified
userProfile.unverified.description=User has not verified their email address
userProfile.active=Active
userProfile.active.description=Whether this user profile is currently active.
userProfile.active.true=Active
userProfile.active.false=Inactive
userProfile.name=Full name
userProfile.name.description=The user''s full name
userProfile.languages=Languages
userProfile.languages.description=
userProfile.about=About
userProfile.location=Location
userProfile.location.description=
userProfile.lastLogin=Last login
userProfile.identifier=Identifier
userProfile.manageUser=Manage User
userProfile.deleteCheck.description=Confirm you want to delete this user.
userProfile.deactivateUser=Deactivate user ''{0}''?
userProfile.email=Email
userProfile.username=Username
userProfile.realname=Full Name
userProfile.password=Password
userProfile.passwordConfirm=Confirm Password
userProfile.groups=Group Membership
userProfile.search=Search Users
userProfile.missingAccount=No account found
userProfile.list=Users
userProfile.create=Create New User
userProfile.create.submit=Create User
userProfile.export=Export active users
userProfile.update=Update User
userProfile.update.item=Update User {0}
userProfile.update.submit=Update User
userProfile.manage=Manage User
userProfile.manage.item=Manage User {0}
userProfile.manage.submit=Submit
userProfile.actions=Action History
userProfile.delete=Delete User
userProfile.delete.item=Delete User {0}
userProfile.delete.confirm=User profiles should almost never be deleted as doing so loses information concerning the provenance of items. A better alternative is to deactivate the profile instead. Please consider this action carefully.
userProfile.delete.check=Type the user''s full name to confirm you want to delete their account.
userProfile.delete.submit=Delete User

#
# Groups
#
group.list=Groups
group.admin=Admin
group.identifier=Group Identifier
group.identifier.description=A short, lower-case, 1-word identifier
group.name=Group Name
group.name.description=A title-case display for the group
group.description=Group Description
group.description.description=A description of this group''s purpose
group.members=Group Members
group.membersPageHeader={2,choice,0#No members found|1#One member|1<Group Members {0,number,integer} to {1,number,integer} of {2,number,integer}}
group.create=Create New Group
group.create.submit=Create Group
group.edit=Update Group
group.edit.item=Update Group {0}
group.edit.submit=Update Group
group.delete=Delete Group
group.delete.item=Delete Group {0}
group.delete.confirm=
group.delete.submit=Delete Group
group.parentGroups=Parent Groups

#
# Item visibility
#
visibility=Visibility
visibility.update=Set Visibility
visibility.update.submit=Submit
visibility.visibleToEveryone=Unrestricted visibility
visibility.visibleToEveryoneMessage=This item currently has no visibility restrictions and is therefore accessible to everyone.
visibility.restrictedVisibility=Restricted Visibility
visibility.restrictedVisibilityMessage=This item is currently visible to the following groups/users:
visibility.visibilityRestrictedTo=Visibility restricted to:
visibility.chooseUsers=Choose Users
visibility.chooseGroups=Choose Groups

# Item promotion
promotion=Promotion
promotion.isPromoted=Promoted By
promotion.notPromoted=Not Promoted

#
# Admin-specific item fields
#

publicationStatus.publicationStatus=Publication Status
publicationStatus.Published=Published
publicationStatus.Draft=Draft

copyrightStatus.copyrightStatus=Copyright Status
copyrightStatus.no=No
copyrightStatus.yes=Copyrighted
copyrightStatus.unknown=Unknown

scope.scope=Scope
scope.high=High
scope.medium=Medium
scope.low=Low

archiveType.archiveType=Archive Type
archiveType.micro=Micro Archive

#
# Permission Grants
#
permissionGrant.heading=Permissions for {0}
permissionGrant.accessor=User/Group
permissionGrant.targets=Targets
permissionGrant.scope=Scope
permissionGrant.grantedBy=Granted By

#
# Permissions
#
permissions.manage.item=Manage Permissions for {0}
permissions.manage=Manage Permissions
permissions.global.update=Update Global Permissions
permissions.global.update.submit=Update Global Permissions
permissions.global.manage=Manage Global Permissions
permissions.global.manage.submit=Update Permissions
permissions.scopeLevel.manage=Scoped Permissions
permissions.scopeLevel.manage.add=Add Scoped Permissions
permissions.scopeLevel.manage.info=Scoped permissions allow you to grant permissions for users or groups to performactions on all items ''within'' another item (the scope), i.e. all documentary units within a given repository.
permissions.scopeLevel.manage.item=Scoped Permissions for {0}
permissions.accessor.scopeLevel.item=Scoped Permissions for {0}
permissions.itemLevel.show=Show Item-level Permissions
permissions.itemLevel.manage=Item-level Permissions
permissions.itemLevel.manage.add=Add Item-level Permissions
permissions.itemLevel.manage.item=Item-level Permissions for {0}
permissions.itemLevel.manage.info=Item-level permissions allow you to grant permissions for users or groups to performactions on a single item, i.e. updating an institution''s descriptive information.
permissions.accessor.itemLevel.item=Item-level Permissions for {0}
permissions.scopeLevel.grant.item=Manage Permissions in scope {0} for {1}
permissions.grant.item=Manage Permissions on {0} for {1}
permissions.grant.submit=Update Permissions
permissions.update=Update Permissions
permissions.update.submit=Update Permissions
permissions.permissionGrants={2,choice,0#No permissions granted|1#One permission granted|1<Displaying grants {0,number,integer} to {1,number,integer} of {2,number,integer}}
permissions.itemPermissionsForItem=Item-level Permissions for {0}
permissions.scopedPermissionsForItem=Scoped Permissions for {0}
permissions.inheritedFrom=Inherited From: {0}
permissions.revoke=Revoke Permission?
permissions.revoke.submit=Revoke



# Messages for archival descriptions (Isdiah & IsadG)

error.badUrlPattern=Invalid URL pattern. The pattern must contain the substitution variable '''{'identifer'}'''and be an otherwise valid URL.

# Admin Misc
actions=Actions


#
# Virtual Units
#
virtualUnit.collection=Virtual Collection
virtualUnit.search=Virtual Collections
virtualUnit.searchInside=Structure
virtualUnit.parentItem=Parent Virtual Item
virtualUnit.noDescriptions=No linked descriptions
virtualUnit.list=Virtual Item Collections
virtualUnit.create=Create Virtual Item
virtualUnit.create.submit=Create Virtual Item
virtualUnit.update=Update Virtual Item
virtualUnit.update.submit=Update
virtualUnit.child.create=Create Child Virtual Item
virtualUnit.createRef=Create Item Reference
virtualUnit.createRef.submit=Create Reference
virtualUnit.deleteRef=Remove Item Reference
virtualUnit.deleteRef.submit=Remove Reference
virtualUnit.identifier=Identifier
virtualUnit.identifier.description=The unique identifier for this virtual unit
virtualUnit.includeRef=Ext. Unit ID
virtualUnit.includeRef.description=The ID of the non-virtual documentary unit referencedby this virtual item. Multiple item IDs can be entered separated by commas.

#
# Documentary Units
#
documentaryUnit.heldBy=Held By
documentaryUnit.parentItem=Parent Item
documentaryUnit.availableDescriptions=Available Descriptions
documentaryUnit.noDescriptions=This item has not yet been provided with any ISAD(G) description info.
documentaryUnit.search=Search Top-level Descriptions
documentaryUnit.searchInside=Structure
documentaryUnit.hasChildItems=Container
documentaryUnit.noChildItems=Single level
documentaryUnit.childCount={0,choice,0#No child items|1#1 child item|1<{0,number,integer} child items}
documentaryUnit.list=Archival Descriptions
documentaryUnit.create=Create Archival Description
documentaryUnit.create.submit=Create Archival Description
documentaryUnit.update=Update Archival Description
documentaryUnit.update.submit=Update Archival Description
documentaryUnit.child.create=Create New Child Item


description.identifier=Description ID (Optional)
description.identifier.description=Optional field to distinguish a description from others that might share the same language code.

#
# Countries
#
country.repositoriesPageHeader={2,choice,0#No institutions found|1#One institution found|1<Institutions {0,number,integer} to {1,number,integer} of {2,number,integer}}
country.search=Search Countries
country.searchInside=Search Institutions
country.childCount={0,choice,0#No institutions listed|1#1 institution listed|1<{0,number,integer} institutions listed}
country.items=Repositories
country.identifier=Country Code
country.identifier.description=The ISO-3166-1 2-letter code for this country
country.abstract=Abstract
country.abstract.description=An abstract of the report for this country
country.report=History
country.report.description=The report describing this country''s archival situation
country.situation=Archival Situation
country.situation.description=The report describing this country''s archival situation
country.dataSummary=EHRI Data (Summary)
country.dataSummary.description=
country.dataExtensive=EHRI Data (Extensive)
country.dataExtensive.description=
country.list=Countries
country.create=Add Country
country.create.submit=Add Country
country.update=Update Country
country.update.submit=Update Country


# Form sections
documentaryUnit.identityArea=Identity Area
documentaryUnit.descriptionsArea=Descriptions
documentaryUnit.administrationArea=Správa
documentaryUnit.contextArea=Context Area
documentaryUnit.contentArea=Content Area
documentaryUnit.conditionsArea=Conditions Area
documentaryUnit.materialsArea=Materials Area
documentaryUnit.notesArea=Notes Area
documentaryUnit.controlArea=Control Area

documentaryUnit.ref.description=If this description is sourced from an online resource enter the URL of that resource here. Note: web page URLs are not necessarily stable identifiers so this should not be considered a replacement for the identifier field. (3.1.1 - extended)
documentaryUnit.name=Title
documentaryUnit.name.description=To name the unit of description.\n\nProvide either a formal title or a concise supplied title in accordance with the rules ofmultilevel description and national conventions.\n\nIf appropriate, abridge a long formal title, but only if this can be done without loss ofessential information.\n\nFor supplied titles, at the higher level, include the name of the creator of the records. Atlower levels one may include, for example, the name of the author of the document and aterm indicating the form of the material comprising the unit of description and, whereappropriate, a phrase reflecting function, activity, subject, location, or theme.\n\nDistinguish between formal and supplied titles according to national or languageconventions. (3.1.2)
documentaryUnit.parallelFormsOfName=Parallel Names
documentaryUnit.parallelFormsOfName.description=

documentaryUnit.identifier=Description Code
documentaryUnit.identifier.description=To identify uniquely the unit of description and to provide alink to the description that represents it.\n\nA specific local reference code, control number, or other unique identifier within the scopeof the holding repository.\n\nNB: Contra ISAD(G) 3.1.1 do not include the country and repositorycode here.
documentaryUnit.otherIdentifiers=Other Ref. Codes

documentaryUnit.dates=Dates
documentaryUnit.dates.description=To identify and record the date(s) of the unit of description.\n\nRecord at least one of the following types of dates for the unit of description, asappropriate to the materials and the level of description.\nDate(s) when records were accumulated in the transaction of business or the conduct ofaffairs;Date(s) when documents were created. This includes the dates of copies, editions, orversions of, attachments to, or originals of items generated prior to their accumulation asrecords.\nIdentify the type of date(s) given. Other dates may be supplied and identified in accordancewith national conventions.\nRecord as a single date or a range of dates as appropriate. A range of dates should alwaysbe inclusive unless the unit of description is a record-keeping system (or part thereof) inactive use. (3.1.3)

documentaryUnit.unitDates=Additional Dates
documentaryUnit.unitDates.description=Legacy field for additonal date data. Prefer dates field when possible.

documentaryUnit.levelOfDescription=Level of Description
documentaryUnit.levelOfDescription.description=To identify the level of arrangement of the unit of description. (3.1.4)

documentaryUnit.collection=Collection
documentaryUnit.subcollection=Sub Collection
documentaryUnit.fonds=Fonds
documentaryUnit.subfonds=Subfonds
documentaryUnit.recordgrp=Record Group
documentaryUnit.subgrp=Sub Record Group
documentaryUnit.series=Series
documentaryUnit.subseries=Subseries
documentaryUnit.file=File
documentaryUnit.item=Item
documentaryUnit.otherlevel=Other Level
documentaryUnit.class=Class
documentaryUnit.creation=Creation
documentaryUnit.existence=Existence

#
# Isad(G)
#
documentaryUnit.physicalLocation.description=The physical location or shelf number of the item(s)within their repository or holding institution. (No direct ISAD(G) field equivalent.)

documentaryUnit.extentAndMedium.description=To identify and describe:\n1. the physical or logical extent and\n2. the medium of the unit of description.\n\nRecord the extent of the unit of description by giving the number of physical or logicalunits in arabic numerals and the unit of measurement. Give the specific medium (media)of the unit of description. (3.1.5)

documentaryUnit.abstract.description=

documentaryUnit.biographicalHistory.description=To provide an administrative history of, or biographical details on,the creator (or creators) of the unit of description to place the material in context and make itbetter understood.\n\nRecord concisely any significant information on the origin, progress, development and workof the organization (or organizations) or on the life and work of the individual (orindividuals) responsible for the creation of the unit of description. If additional informationis available in a published source, cite the source. (3.2.2)

documentaryUnit.archivalHistory.description=To provide information on the history of the unit of descriptionthat is significant for its authenticity, integrity and interpretation.\n\nRecord the successive transfers of ownership, responsibility and/or custody of the unit ofdescription and indicate those actions, such as history of the arrangement, production ofcontemporary finding aids, re-use of the records for other purposes or software migrations,that have contributed to its present structure and arrangement. Give the dates of theseactions, insofar as they can be ascertained. If the archival history is unknown, record thatinformation.\n\nOptionally, when the unit of description is acquired directly from the creator, do not recordan archival history but rather, record this information as the Immediate source ofacquisition. (3.2.3)

documentaryUnit.acquisition.description=To identify the immediate source of acquisition or transfer.\n\nRecord the source from which the unit of description was acquired and the date and/ormethod of acquisition if any or all of this information is not confidential. If the source isunknown, record that information. Optionally, add accession numbers or codes. (3.2.4)

documentaryUnit.scopeAndContent.description=To enable users to judge the potential relevance of the unit of description.\n\nGive a summary of the scope (such as, time periods, geography) and content, (such asdocumentary forms, subject matter, administrative processes) of the unit of description,appropriate to the level of description. (3.3.1)

documentaryUnit.appraisal.description=To provide information on any appraisal, destruction and scheduling action.\n\nRecord appraisal, destruction and scheduling actions taken on or planned for the unit ofdescription, especially if they may affect the interpretation of the material. (3.3.2)

documentaryUnit.accruals.description=To inform the user of foreseen additions to the unit of description.\n\nIndicate if accruals are expected. Where appropriate, give an estimate of their quantity andfrequency. (3.3.3)

documentaryUnit.systemOfArrangement.description=To provide information on the internal structure, the orderand/or the system of classification of the unit of description.\n\nSpecify the internal structure, order and/or the system of classification of the unit ofdescription. Note how these have been treated by the archivist. For electronic records,record or reference information on system design. (3.3.4)

documentaryUnit.conditionsOfAccess.description=To provide information on the legal status or otherregulations that restrict or affect access to the unit of description.\n\nSpecify the law or legal status, contract, regulation or policy that affects access to the unitof description. Indicate the extent of the period of closure and the date at which thematerial will open when appropriate. (3.4.1)

documentaryUnit.conditionsOfReproduction.description=To identify any restrictions on reproductionof the unit of description.\n\nGive information about conditions, such as copyright, governing the reproduction of theunit of description after access has been provided. If the existence of such conditions isunknown, record this. If there are no conditions, no statement is necessary. (3.4.2)

documentaryUnit.languageOfMaterial.description=To identify the language(s) employed in the unit of description.\n\nRecord the language(s) and/or script(s) of the materials comprising the unit of description.Note any distinctive alphabets, scripts, symbol systems or abbreviations employed. (3.4.3)

documentaryUnit.scriptOfMaterial.description=To identify the script(s) employed in the unit of description.\n\nRecord the language(s) and/or script(s) of the materials comprising the unit of description.Note any distinctive alphabets, scripts, symbol systems or abbreviations employed. (3.4.3)

documentaryUnit.physicalCharacteristics.description=To provide information about any important physicalcharacteristics or technical requirements that affect use of the unit of description.\n\nIndicate any important physical conditions, such as preservation requirements, that affectthe use of the unit of description. Note any software and/or hardware required to accessthe unit of description. (3.4.4)

documentaryUnit.findingAids.description=To identify any finding aids to the unit of description.\n\nGive information about any finding aids that the repository or records creator may havethat provide information relating to the context and contents of the unit of description.If appropriate, include information on where to obtain a copy. (3.4.5)

documentaryUnit.locationOfOriginals.description=To indicate the existence, location, availabilityand/or destruction of originals where the unit of description consists of copies.\n\nIf the original of the unit of description is available (either in the institution or elsewhere)record its location, together with any significant control numbers. If the originals no longerexist, or their location is unknown, give that information. (3.5.1)

documentaryUnit.locationOfCopies.description=To indicate the existence, location and availabilityof copies of the unit of description.\n\nIf the copy of the unit of description is available (either in the institution or elsewhere)record its location, together with any significant control numbers. (3.5.2)

documentaryUnit.relatedUnitsOfDescription.description=To identify related units of description.\n\nRecord information about units of description in the same repository or elsewhere that arerelated by provenance or other association(s). Use appropriate introductory wording andexplain the nature of the relationship . If the related unit of description is a finding aid, usethe finding aids element of description (3.4.5) to make the reference to it. (3.5.3)

documentaryUnit.separatedUnitsOfDescription.description=Information about materials that are associated by provenanceto the described materials but that have been physically separated or removed. Items may be separated for variousreasons, including the dispersal of special formats to more appropriate custodial units; the outright destructionof duplicate or nonessential material; and the deliberate or unintentional scattering of fonds among differentrepositories. Do not confuse with <relatedmaterial>, which is used to encode descriptions of or references tomaterials that are not physically or logically included in the material described in the finding aid but thatmay be of use to a reader because of an association to the described materials. (3.5.3)

documentaryUnit.publicationNote.description=To identify any publications that are about or are based on the use, study, or analysis of the unit of description.\n\nRecord a citation to, and/or information about a publication that is about or based on theuse, study, or analysis of the unit of description. Include references to published facsimilesor transcriptions. (3.5.4)

documentaryUnit.notes.description=To provide information that cannot be accommodated in any of the other areas.\n\nRecord specialized or other important information not accommodated by any of thedefined elements of description. (3.6.1)

documentaryUnit.archivistNote.description=To explain how the description was prepared and by whom. (3.7.1)

documentaryUnit.sources.description=Record notes on sources consulted in preparing thedescription and who prepared it. (3.7.1)

documentaryUnit.rulesAndConventions.description=To identify the protocols on which the description is based.\n\nRecord the international, national and/or local rules or conventions followed in preparingthe description. (3.7.2)

documentaryUnit.datesOfDescriptions.description=To indicate when this description was prepared and/or revised.\n\nRecord the date(s) the entry was prepared and/or revised. (3.7.3)

documentaryUnit.processInfo.description=Information about accessioning, arranging, describing, preserving, storing,or otherwise preparing the described materials for research use. Specific aspects of each of these activities may be encoded separately within other elements, such as acquisition (3.2.4), arrangement (3.3.4), extent and medium (3.1.5), etc. This is a synthetic field not strictly presentin the ISAD(G) specification but intended to contain more general process information as given withinthe EAD <processinfo> field.

documentaryUnit.accessPoints=Access Points

# isad(g) dates
documentaryUnit.startDate=Start Date
documentaryUnit.endDate=End Date

#
# ISDIAH Field definitions
#

# Form sections
#
# Repositories
#
repository.search=Search Institutions
repository.searchInside=Search items held by {0}
repository.itemsHeldOnline=Items on EHRI
repository.hasChildItems=Yes
repository.noChildItems=No
repository.childCount={0,choice,0#No archival descriptions available|1#1 archival description available|1<{0,number,integer} archival descriptions available}
repository.list=Institutions
repository.create=Create Institution
repository.create.submit=Create Institution
repository.update=Update Institution
repository.update.submit=Update Institution

repository.descriptionArea=Description Area
repository.identity=Identity Area
repository.contact=Contact Area
repository.addresses=Address Area
repository.addresses.create=Add Address
repository.access=Access Area
repository.services=Services Area
repository.administrationArea=Správa
repository.logo=Institution Logo
repository.logo.edit=Edit Institution Logo
repository.logo.update.logMessage=Update logo image
repository.data.manage=EAD Manager
repository.data=EAD Manager - {0}
repository.data.upload=Upload Files
repository.data.upload.submit=Submit
repository.data.upload.files=Files
repository.data.upload.files.empty=Repository has no data.
repository.data.upload.file.name=File Name
repository.data.upload.file.lastModified=Last Modified
repository.data.upload.file.size=Size
repository.data.upload.file.delete=Delete

#
# ISDIAH
#
repository.identityArea=Identity Area
repository.identifier.description=EHRI identifier field. This is an auto-generated number.

repository.name.description=To create an authorised access point that uniquely identifies the institution witharchival holdings.\n\nRecord the standardised form of name of the institution with archival holdings, addingappropriate qualifiers (for instance dates, place, etc.), if necessary. Specify separatelyin the Rules and/or conventions used element (5.6.3) which set of rules has beenapplied for this element. (5.1.2)

repository.parallelFormsOfName.description=To indicate the various forms in which the authorised form of name of an institution with archival holdings occurs in other languages or script form(s).\n\nRecord the parallel form(s) of name of the institution with archival holdings inaccordance with any relevant national or international conventions or rules applied bythe agency that created the description, including any necessary sub elements and/orqualifiers required by those conventions or rules. Specify in the Rules and/orconventions used element (5.6.3) which rules have been applied. (5.1.3)

repository.otherFormsOfName.description=To indicate any other name(s) for the institution with archival holdings not used elsewhere in the Identity Area.\n\nRecord any other name(s) by which the institution with archival holdings may beknown. This could include other forms of the same name, acronyms, otherinstitutional names, or changes of name over time, including, if possible, relevant dates. (5.1.4)

repository.institutionType.description=To identify the type of an institution with archival holdings.\n\nRecord the type of the institution with archival holdings.\n\nNote: different consistent systems of criteria can be used and/or combined to classifyinstitutions with archival holdings, in accordance with any relevant national orinternational conventions, rules or controlled vocabularies. (5.1.5)

# AddressF
repository.addressArea=Address Area
repository.addressName=Address Name/Type
repository.addressName.description=A short descriptive indicating the type of address given belowe.g ''primary''

repository.municipality.description=The city in which this institution resides.
repository.firstdem.description=Autonomous community or region
repository.street.description=
repository.postalCode.description=
repository.email.description=
repository.telephone.description=
repository.fax.description=
repository.webpage.description=

repository.contactPerson.description=To provide users with all the information needed to contact members of staff.\n\nRecord the name, the contact details and the position of the members of staff (firstname, surname, area of responsibility, email, etc.). This information may relate to theAdministrative structure element (5.3.4).

# Description
repository.history.description=To provide a concise history of the institution with archival holdings.\n\nRecord any relevant information about the history of the institution with archivalholdings. This element may include information on dates of establishment, changes ofnames, changes of legislative mandates, or of any other sources of authority for theinstitution with archival holdings. (5.3.1)

repository.geoculturalContext.description=To provide information about the geographical and cultural context of the institution with archival holdings.\n\nIdentify the geographical area the institution with archival holdings belongs to.Record any other relevant information about the cultural context of the institution witharchival holdings. (5.3.2)

repository.mandates.description=To indicate the sources of authority for the institution with archival holdings in terms of its powers, functions, responsibilities or sphere of activities, including territorial.\n\nRecord any document, law, directive or charter which acts as a source of authority forthe powers, functions and responsibilities of the institution with archival holdings,together with information on the jurisdiction(s) and covering dates when themandate(s) applied or were changed. (5.3.3)

repository.administrativeStructure.description=To represent the current administrative structureof the institution with archival holdings.\n\nDescribe, in narrative form or using organisational charts, the current administrativestructure of the institution with archival holdings.(5.3.4)

repository.records.description=To provide information about the records management and collectingpolicies of the institution with archival holdings.\n\nRecord information about the records management and collecting policies of theinstitution with archival holdings. Define the scope and nature of material which theinstitution with archival holdings accessions. Indicate whether the repository seeks toacquire archival materials by transfer, gift, purchase and/or loan. If the policy includesactive survey and/or rescue work, this might be spelt out. (5.3.5)

repository.buildings.description=To provide information about the building(s) of the institutionwith archival holdings.\n\nRecord information on the building(s) of the institution with archival holdings(general and architectural characteristics of the building, capacity of storage areas,etc). Where possible, provide information which can be used for generating statistics. (5.3.6)


repository.holdings.description=To provide a profile of the holdings of the institution.\n\nRecord a short description of the holdings of the institution with archival holdings,describing how and when they were formed. Provide information on volume ofholdings, media formats, thematic coverage, etc. See chapter 6 for guidance on how toestablish links to archival databases and/or detailed descriptions of the holdings. (5.3.7)

repository.findingAids.description=To provide a general overview of the published and/orunpublished finding aids and guides prepared by the institution with archival holdings and any other relevant publications.\n\nRecord the title and other pertinent details of the published and/or unpublished findingaids and guides prepared by the institution with archival holdings and of any otherrelevant publications. Use ISO 690 Information and documentation ? Bibliographicreferences and other national or international cataloguing rules. See chapter 6 forguidance on how to establish links to online archival catalogues and/or finding aids. (5.3.8)

# Access
repository.accessArea=Access Area
repository.accessArea.description=

repository.openingTimes.description=To provide information on opening times and dates of annual closures.\n\nRecord the opening hours of the institution with archival holdings and annual,seasonal and public holidays, and any other planned closures. Record timesassociated with the availability and/or delivery of services (for example, exhibitionspaces, reference services, etc.). (5.4.1)

repository.conditions.description=To provide information about the conditions, requirementsand procedures for access to, and use of institutional services.\n\nDescribe access policies, including any restrictions and/or regulations for the use ofmaterials and facilities. Record information about registration, appointments, readers?tickets, letters of introduction, admission fees, etc. Where appropriate, make referenceto the relevant legislation. (5.4.2)

repository.accessibility.description=To provide accessibility information related to theinstitution with archival holdings and its services.\n\nRecord information about travelling to the institution with archival holdings anddetails for users with disabilities, including building features, specialised equipmentor tools, parking or lifts. (5.4.3)

# Services
repository.servicesArea=Services Area
repository.servicesArea.description=

repository.researchServices.description=To describe the research services provided by theinstitution with archival holdings.\n\nRecord information about the onsite services provided by the institution with archivalholdings such as languages spoken by staff, research and consultation rooms, enquiryservices, internal libraries, map, microfiches, audio-visual, computer rooms, etc.Record as well any relevant information about research services, such as researchundertaken by the institution with archival holdings, and the fee charge if applicable. (5.5.1)

repository.reproductionServices.description=To provide information about reproduction services.\n\nRecord information about reproduction services available to the public (microfilms,photocopies, photographs, digitised copies). Specify general conditions andrestrictions to the services, including applicable fees and publication rules. (5.5.2)

repository.publicAreas.description=To provide information about areas of the institutionavailable for public use.\n\nRecord information about spaces available for public use (permanent or temporaryexhibitions, free or charged internet connection, cash machines, cafeterias, restaurants,shops, etc.). (5.5.3)

# Control
repository.controlArea=Control Area
repository.controlArea.description=

repository.descriptionIdentifier.description=To identify the description of the institution with archival holdings uniquely within the context in which it will be used.\n\nRecord a unique description identifier in accordance with local and/or nationalconventions. If the description is to be used internationally, record the code of thecountry in which the description was created in accordance with the latest version ofISO 3166 - Codes for the representation of names of countries. Where the creator ofthe description is an international organisation, give the organisational identifier inplace of the country code. (5.6.1)

repository.institutionIdentifier.description=To identify the agency(ies) responsible for the description.\n\nRecord the full authorised form of name(s) of the agency(ies) responsible for creating,modifying or disseminating the description or, alternatively, record a code for theagency in accordance with the national or international agency code standard. (5.6.2)

repository.rulesAndConventions.description=To identify the national or international conventionsor rules applied in creating the description.\n\nRecord the names, and, where useful, the editions or publication dates of theconventions or rules applied. Specify, separately, which rules have been applied forcreating the Authorised form(s) of name. Include reference to any system(s) of datingused to identify dates in this description (e.g. ISO 8601). (5.6.3)

repository.status.description=To indicate the drafting status of the description so that users can understand the current status of the description.\n\nRecord the current status of the description, indicating whether it is a draft, finalizedand/or revised or deleted. (5.6.4)

repository.levelOfDetail.description=To indicate whether the description applies a minimal,partial or a full level of detail.\n\nRecord whether the description consists of a minimal, partial or full level of detail inaccordance with relevant international and/or national guidelines and/or rules. In theabsence of national guidelines or rules, minimal descriptions are those that consistonly of the three essential elements of an ISDIAH compliant description (see 4.7),while full descriptions are those that convey information for all relevant ISDIAHelements of description. (5.6.5)

repository.datesCVD.description=To indicate when this description was created, revised or deleted.\n\nRecord the date the description was created and the dates of any revisions to thedescription. Specify in the Rules and/or conventions used element (5.6.3) thesystem(s) of dating used, e.g. ISO 8601. (5.6.6)

repository.sources.description=To indicate the sources consulted in creating the descriptionof the institution with archival holdings.\n\nRecord the sources consulted in establishing the description of the institution witharchival holdings. (5.6.8)

repository.maintenanceNotes.description=To document additional information relating to thecreation of and changes to the description.\n\nRecord notes pertinent to the creation and maintenance of the description. Forexample, the names of persons responsible for creating and/or revising the descriptionmay be recorded here. (5.6.9)

repository.urlPattern=Item URL Pattern
repository.urlPattern.description=The URL pattern is used to provide automatic linking from items on EHRI to the source institution''s website, providing that EHRI identifiers are also used to identify the items on the other institution''s site. The given URL must be a valid except for the substitution of '''{'identifier'}''' where the item''s identifier should go.

repository.logoUrl=Logo URL
repository.logoUrl.description=A URL to a logo image for the repository.

repository.priority=Priority
repository.priority.five=Five
repository.priority.four=Four
repository.priority.three=Three
repository.priority.two=Two
repository.priority.one=One
repository.priority.zero=Zero
repository.priority.reject=Reject
repository.priority.unknown=-

oaipmh.submit=Harvest
oaipmh=OAI-PMH Harvesting
oaipmh.url=Endpoint URL
oaipmh.url.description=The base URL of the OAI-PMH endpoint
oaipmh.format=Metadata Format
oaipmh.format.description=The OAI-PMH metadata prefix parameter
oaipmh.successCount=Items successfully harvested: {0}
oaipmh.error.cannotDisseminateFormat=Format not supported for metadata prefix
oaipmh.error.badArgument=Invalid set name, metadata format, or other argument: {0}
oaipmh.error.noMetadataFormats=No metadata formats available for the requested item
oaipmh.error.noSetHierarchy=Sets are not supported by this repository
oaipmh.error.noRecordsMatch=No records found for this combination of arguments
oaipmh.error.badResumptionToken=The resumption token is incorrect or has expired. This should not ordinarily happen.
oaipmh.error.badVerb=The repository reported a ''bad verb'' error. This should not ordinarily happen.
oaipmh.error.idDoesNotExist=The repository reported that item with id ''{0}'' does not exist. This should not ordinarily happen.
oaipmh.error.url=Endpoint URL responded with an invalid status
oaipmh.error.invalidXml=Endpoint URL responded with invalid XML content


resourceSync.error.notFound=Resource not found: {0}
resourceSync.error.unexpectedStatus=Unexpected HTTP status code: {0}
resourceSync.error.badFormat=Resource cannot be parsed as XML: {0}

# Authority i18n messages


#
# Authority set
#
authoritativeSet.items=Authority Items
authoritativeSet.identifier=Identifier
authoritativeSet.identifier.description=A short, lowercase, 1-word identifier
authoritativeSet.name=Name
authoritativeSet.name.description=A title-case display name for this set
authoritativeSet.description=Description
authoritativeSet.description.description=A description of this set''s purpose
authoritativeSet.list=Authority Sets
authoritativeSet.create=Create New Authority Set
authoritativeSet.create.submit=Create Authority Set
authoritativeSet.update=Update Authority Set
authoritativeSet.update.submit=Update Authority Set


#
# Historical Agents
#
historicalAgent.search=Search Authorities
historicalAgent.authoritativeSet=Set
historicalAgent.list=Authorities
historicalAgent.create=Create Authority
historicalAgent.create.submit=Create Authority
historicalAgent.update=Update Authority
historicalAgent.update.submit=Update Authority

#
# ISAAR Field definitions
#

# Form sections
historicalAgent.descriptionArea=Description Area
historicalAgent.identity=Identity Area
historicalAgent.services=Services Area
historicalAgent.administrationArea=Správa

# Identity Section
historicalAgent.identityArea=Identity Area

historicalAgent.dates=Dates
historicalAgent.creation=Creation
historicalAgent.existence=Existence

historicalAgent.identifier.description=To identify the authority record uniquely within thecontext in which it will be used. (5.4.1)

historicalAgent.name.description=To create an authorized access point that uniquely identifies a corporate body, person or family.\n\nRecord the standardized form of name for the entity being described in accordance withany relevant national or international conventions or rules applied by the agency thatcreated the authority record. Use dates, place, jurisdiction, occupation, epithet and otherqualifiers as appropriate to distinguish the authorized form of name from those of otherentities with similar names. Specify separately in the Rules and/or conventions element(5.4.3) which set of rules has been applied for this element.

historicalAgent.parallelFormsOfName.description=To indicate the various forms in which the Authorizedform of name occurs in other languages or script forms(s).\n\nRecord the parallel form(s) of name in accordance with any relevant national orinternational conventions or rules applied by the agency that created the authority record,including any necessary sub elements and/or qualifiers required by those conventions orrules. Specify in the Rules and/or conventions element (5.4.3) which rules have beenapplied

historicalAgent.standarisedFormsOfName.description=To indicate standardized forms of name for the corporatebody, person or family that have been constructed according to rules other than thoseused to construct the authorised form of name. This can facilitate the sharing of authorityrecords between different professional communities.\n\nRecord the standardized form of name for the entity being described in accordance withother conventions or rules. Specify the rules and/or if appropriate the name of the agencyby which these standardized forms of name have been constructed.

historicalAgent.otherFormsOfName.description=To indicate any other name(s) for the corporate body, person orfamily not used elsewhere in the Identity Area.\n\nRecord other names by which the entity may be known, such as:\n\n1. other forms of the same name, e.g. acronyms;\n2. other names of corporate bodies, for example, changes of name over time and their dates;3\n3. other names of persons or families, for example, changes of name over time with theirdates including pseudonyms, maiden names, etc;\n4. names and prenominal and postnominal titles of persons and families, e.g. titles ofnobility, or titles of honour held by the individual or family.

historicalAgent.languageCode=Language of Description
historicalAgent.languageCode.description=

historicalAgent.typeOfEntity.description=To indicate whether the entity being described is a corporate body, person or family.\n\nSpecify the type of entity (corporate body, person or family) that is being described in thisauthority record.


historicalAgent.corporateBody=Corporate Body
historicalAgent.family=Family
historicalAgent.person=Person

historicalAgent.datesOfExistence.description=To indicate the dates of existence of the corporate body, person or family.\n\nRecord the dates of existence of the entity being described. For corporate bodies includethe date of establishment/foundation/enabling legislation and dissolution. For personsinclude the dates or approximate dates of birth and death or, when these dates are notknown, floruit dates. Where parallel systems of dating are used, equivalences may berecorded according to relevant conventions or rules. Specify in the Rules and/orconventions element (5.4.3) the system(s) of dating used, e.g. ISO 8601.

historicalAgent.biographicalHistory.description=To provide a concise history of the corporate body, person or family.\n\nRecord in narrative form or as a chronology the main life events, activities, achievementsand/or roles of the entity being described. This may include information on gender,nationality, family and religious or political affiliations. Wherever possible, supply datesas an integral component of the narrative description.

historicalAgent.place.description=To indicate the predominant places and/or jurisdictions where the corporate body, personor family was based, lived or resided or had some other connection.\n\nRecord the name of the predominant place(s)/jurisdiction(s), together with the nature andcovering dates of the relationship with the entity.

historicalAgent.legalStatus.description=To indicate the legal status of a corporate body.\n\nRecord the legal status and where appropriate the type of corporate body together with thecovering dates when this status applied.

historicalAgent.functions.description=To indicate the functions, occupations and activities performed by thecorporate body, person or family.\n\nRecord the functions, occupations and activities performed by the entity being described,together with the covering dates when useful. If necessary, describe the nature of thefunction, occupation or activity.

historicalAgent.mandates.description=To indicate the sources of authority for the corporate body, personor family in terms of its powers, functions, responsibilities or sphere of activities,including territorial.\n\nRecord any document, law, directive or charter which acts as a source of authority for thepowers, functions and responsibilities of the entity being described, together withinformation on the jurisdiction(s) and covering dates when the mandate(s) applied or werechanged.

historicalAgent.structure.description=To describe and/or represent the internal administrative structure(s)of a corporate body or the genealogy of a family.\n\nDescribe the internal structure of a corporate body and the dates of any changes to thatstructure that are significant to the understanding of the way that corporate bodyconducted its affairs (e.g. by means of dated organization charts).Describe the genealogy of a family (e.g. by means of a family tree) in a way thatdemonstrates the inter-relationships of its members with covering dates.

historicalAgent.generalContext.description=To provide significant information on the general social,cultural, economic, political and/or historical context in which the corporate body, personor family operated, lived or was active.\n\nProvide any significant information on the social, cultural, economic, political and/orhistorical context in which the entity being described operated.

# Control
historicalAgent.controlArea=Control Area

historicalAgent.descriptionIdentifier=Description Identifier
historicalAgent.descriptionIdentifier.description=

historicalAgent.institutionIdentifier.description=To identify the agency(ies) responsible for the authority record.\n\nRecord the full authorized form of name(s) of the agency(ies) responsible for creating,modifying or dissemninating the authority record or, alternatively, record a code for theagency in accordance with the national or international agency code standard. Include reference to any systems of identification used to identify the institutions (e.g. ISO15511).

historicalAgent.rulesAndConventions.description=To identify the national or international conventionsor rules applied in creating the archival authority record.\n\nRecord the names and where useful the editions or publication dates of the conventions orrules applied. Specify separately which rules have been applied for creating theAuthorized form of name. Include reference to any system(s) of dating used to identifydates in this authority record (e.g. ISO 8601).

historicalAgent.status.description=To indicate the drafting status of the authority recordso that users can understand the current status of the authority record.\n\nRecord the current status of the authority record, indicating whether the record is a draft,finalized and/or revised or deleted.

historicalAgent.levelOfDetail.description=To indicate whether the authority record applies a minimal,partial or a full level of detail.\n\nIndicate whether the record consists of a minimal, partial or full level of detail inaccordance with relevant international and/or national guidelines and/or rules. In theabsence of national guidelines or rules, minimal records are those that consist only of thefour essential elements of an ISAAR(CPF) compliant authority record (see 4.8), while fullrecords are those that convey information for all relevant ISAAR(CPF) elements ofdescription.

historicalAgent.datesCVD.description=To indicate when this authority record was created, revised or deleted.\n\nRecord the date the authority record was created and the dates of any revisions to therecord. Specify in the Rules and/or conventions element (5.4.3) the system(s) of datingused, e.g. ISO 8601.

historicalAgent.languages.description=To indicate the language(s) used to create the authority record.

historicalAgent.scripts.description=To indicate the script(s) used to create the authority record.

historicalAgent.source.description=To identify the sources consulted in creating the authority record.\n\nRecord the sources consulted in establishing the authority record.

historicalAgent.maintenanceNotes.description=To document the creation of and changes to the authority record.\n\nRecord notes pertinent to the creation and maintenance of the authority record. Thenames of persons responsible for creating the authority record may be recorded here.

historicalAgent.accessPoints=Access Points


# Skos/Concept i18n definitions

#
# Vocabularies
#
cvocVocabulary.identifier=Identifier
cvocVocabulary.identifier.description=A short, lowercase, 1-word identifier
cvocVocabulary.name=Name
cvocVocabulary.name.description=A title-case display name for this vocabulary
cvocVocabulary.description.description=A description of this vocabulary''s purpose
cvocVocabulary.description=Description
cvocVocabulary.items=Search Terms
cvocVocabulary.search=Search Vocabularies
cvocVocabulary.list=Vocabularies
cvocVocabulary.create=Create New Vocabulary
cvocVocabulary.create.submit=Create Vocabulary
cvocVocabulary.update=Update Vocabulary
cvocVocabulary.update.submit=Update Vocabulary
cvocVocabulary.items.edit=Concept Editor
cvocVocabulary.items.editor={0}: Concept Editor

#
# Concepts
#
cvocConcept.createDescription=Add Concept Description
cvocConcept.deleteDescription=Delete Description
cvocConcept.list=Terms
cvocConcept.create=Create New Term
cvocConcept.create.submit=Create Term
cvocConcept.update=Update Term
cvocConcept.update.submit=Update Term
cvocConcept.topLevel.create=Create Top-level Term
cvocConcept.topLevel.list={2,choice,0#No top-level terms found|1#One top-level term found|1<Top-level terms {0,number,integer} to {1,number,integer} of {2,number,integer}}
cvocConcept.narrower.create=Create Narrower Term
cvocConcept.broaderTerms.set=Set Broader Terms
cvocConcept.name.description=The preferred lexical label for a resource, in a given language.
cvocConcept.altLabel.description=Acronyms, abbreviations, spelling variants, and irregular plural/singular forms may be included among the alternative labels for a concept.Mis-spelled terms are normally included as hidden labels (see skos:hiddenLabel).
cvocConcept.hiddenLabel.description=A lexical label for a resource that should be hidden when generating visual displays of the resource,but should still be accessible to free text search operations.
cvocConcept.note.description=A general note, for any purpose.
cvocConcept.changeNote.description=A note about a modification to a concept.
cvocConcept.editorialNote.description=A note for an editor, translator or maintainer of the vocabulary.
cvocConcept.historyNote.description=A note about the past state/use/meaning of a concept.
cvocConcept.scopeNote.description=A note that helps to clarify the meaning and/or the use of a concept.
cvocConcept.definition.description=A statement or formal explanation of the meaning of a concept.

#
# Access points
#
accessPoint=Access Points
accessPoint.editing=Editing access points for description: "{0}"
accessPoint.create=Add New
accessPoint.create.submit=Save
accessPoint.newAccessPoint=New Access Point
accessPoint.newTextAccessPoint.description=Create a new text-only access point
accessPoint.name=Name of link target
accessPoint.description=Optional description...
# References not on this item, but from another item's access points
accessPoint.delete=Remove Access Point
accessPoint.delete.submit=Remove

#
# Date Periods
#
datePeriod.type=Type
datePeriod.type.creation=Creation
datePeriod.type.existence=Existence
datePeriod.precision=Precision
datePeriod.precision.year=Year
datePeriod.precision.quarter=Quarter
datePeriod.precision.month=Month
datePeriod.precision.week=Week
datePeriod.precision.day=Day


#
# System Events
#
systemEvent.itemAtTime=Action at {0}
systemEvent.lastUpdated=Updated {0}
systemEvent.creation=Item created
systemEvent.createDependent=Description added
systemEvent.modification=Item modified
systemEvent.modifyDependent=Description modified
systemEvent.deletion=Item deleted
systemEvent.deletion.noInfo=No item info available
systemEvent.deletion.itemInfo=Item type {0} with id ''{1}''{2,choice,0#|1# and one other item|1< and {2} more items}
systemEvent.deleteDependent=Description deleted
systemEvent.link=Link created
systemEvent.annotation=Annotation created
systemEvent.setGlobalPermissions=Global permissions modified
systemEvent.setItemPermissions=Item-level permissions modified
systemEvent.setVisibility=Visibility modified
systemEvent.addGroup=Member added to group
systemEvent.removeGroup=Member removed from group
systemEvent.ingest=Items imported
systemEvent.promotion=Item Promoted
systemEvent.block=User Blocked
systemEvent.unblock=User Unblocked
systemEvent.demotion=Item Promotion Removed
systemEvent.unknown=Unknown action
systemEvent.scope=Scope
systemEvent.subjects=Subjects
systemEvent.subjects.list={1,choice,-1#No subjects|0#{0}|0<{0} and {1,number,integer} more...}
systemEvent.newerEvents=More recent events
systemEvent.olderEvents=Older events
systemEvent.filter=Filter Events
systemEvent.user.description=Filter events not initiated by these users.
systemEvent.user=Users
systemEvent.et=Event Type
systemEvent.et.description=Exclude events not matching these types.
systemEvent.list=System Events

#
# Common ingest messages
#
ingest=Ingest
ingest.header=Ingest Data: {0}
ingest.submit=Submit
ingest.monitor=Ingest Progress
ingest.sync.warning.note=Warning
ingest.sync.warning=Sync ingest will delete items not present in the incoming data unless their IDs are explicitly excluded in the excluded IDs section.
ingest.sync.confirm=Confirm synchronisation
ingest.parameters=Parameters
ingest.parameters.advanced=Advanced
ingest.allow-update=Allow item updates
ingest.allow-update.description=Permit the importer to update existing items
ingest.tolerant=Tolerant
ingest.tolerant.description=Continue importing items even if there are individual validation errors
ingest.ex=Excluded IDs
ingest.ex.description=Items IDs, one per line, to exclude from post-synchronisation removal if they are not in the ingest data
ingest.log=Log message
ingest.lang=Default language
ingest.lang.description=The default language when not specified in the ingest data
ingest.log.description=Tell people what you''re doing
ingest.data=Data file
ingest.data.description=A single XML file or an archive containing multiple files
ingest.handler=Handler class (Optional)
ingest.handler.description=The fully-qualified class name of the XML handler class
ingest.importer=Importer class (Optional)
ingest.importer.description=The fully-qualified class name of the XML importer class
ingest.properties=Import properties (Optional)
ingest.properties.description=A properties file
ingest.baseURI=Base URI
ingest.baseURI.description=The common URI prefix for items in this vocabulary.
ingest.suffix=URI suffix
ingest.suffix.description=The common URI suffix for items in this vocabulary.
ingest.format.ead-sync=EAD Synchronisation
ingest.format.eac=EAC
ingest.format.skos=SKOS
ingest.commit=Commit Ingest
ingest.commit.description=Apply the ingest. Leave unchecked to dry-run.

ingest.sourceFiles=Ingest Sources
ingest.datasets=Import Datasets

#
# Geocoding
#
geocode.monitor=Geocoding {0}

#
# Harvesting
#
harvesting.starting=Starting harvest with job id: {0}
harvesting.syncingFiles={0,choice,0#No files to sync|1#Syncing one file|1<Syncing {0,number,integer} files}
harvesting.cancelled={0}: cancelled after {1,choice,0#0 files|1#1 file|1<after {1,number,integer} files} in {2,choice,0#0 seconds|1#1 second|1<{2,number,integer} seconds}
harvesting.completed={0}: synced {1,choice,0#0 new files|1#1 new file|1<{1,number,integer} files} in {2,choice,0#0 seconds|1#1 second|1<{2,number,integer} seconds}
harvesting.nothingToDo={0}: nothing new to sync
harvesting.earliestDate=Harvesting from earliest date
harvesting.fromDate=Harvesting from {0}
harvesting.untilDate=Harvesting until {0}
harvesting.error={0}: harvesting error: {1}
harvesting.error.invalidUrl=Invalid harvest URL
harvesting.error.invalidFileName=Invalid file name

#
# Conversion
#
conversion.starting=Starting convert with job id: {0}
conversion.counting=Counting files...
conversion.counted=Total of {0,choice,0#0 files|1#1 file|1<{0,number,integer} files}
conversion.cancelled={0}: cancelled after {1,choice,0#0 files|1#1 file|1<after {1,number,integer} files} in {2,choice,0#0 seconds|1#1 second|1<{2,number,integer} seconds}
conversion.completed={0}: converted {1,choice,0#0 files|1#1 file ({2,number,integer} fresh)|1<{1,number,integer} files ({2,number,integer} fresh)} in {3,choice,0#0 seconds|1#1 second|1<{3,number,integer} seconds}
conversion.error={0}: conversion error: {1}
conversion.unrecoverableError={0}: unexpected conversion error: {1}
