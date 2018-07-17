# Models

Frontend models have some peculiarities compared to some other popular web frameworks, so much so
that they probably shouldn't be called "models" in the first place:

 1. they are composed of two separate parts: internal data and metadata
 2. they do **no** direct data access, instead serving more as simple data structures with some additional convenience methods

The reason for 1) is to make explicit the separation between data that is "owned" by the model item itself, and
that which is relational and must be updated elsewhere. This has a practical component, in that whilst the model's
internal data and metadata must be read from the backend service, only the internal data has to be written to it, which
greatly simplifies serialization. Additionally, internal data often has to be deserialized from other sources, namely
from web forms (and in some cases, client side JSON), and writing form deserializers is much simpler when all the item
metadata can be ignored.

The way this internal/metadata duality manifests itself is best described from example: the model for the documentary
unit type is called `DocumentaryUnit`, which is a `Model`. `Model`s have a `data` field containing their
internal data, which for the documentary unit is `DocumentaryUnitF` (a `ModelData` instance, the `F` suffix standing for "form" (which is
unsatisfactory and subject to change!). Alongside the `data` field, `DocumentaryUnit` also has various metadata fields,
such as it's `holder` (a repository) and its `parent` (another documentary unit, if it exists), which are not directly
updatable when a particular documentary unit is modified.

The principal reason for 2) is that data access is assumed to be asynchronous (and slow) and taken care of by a separate
service layer. This has the side effect of enforcing what is typically deemed a best-practice by separating logic (e.g.
data access) and presentation.


