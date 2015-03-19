All of the EHRI "portal" views are currently namespaced within this "p" package.
This is a temporary situation indended to reduce accidental collisions between
views in the "admin" part of the app. They currently use different versions of
Bootstrap so aren't really compatible.Eventually the "admin" part of the app
will be ported to Bootstrap 3 and common re-usable components drawn into a single
package.

(This temporary package is not called "portal" because that collides with the
`controllers.portal` package automatically imported into all views.)