Miscellaneous Gotchas
=====================

Links to avatar images are broken
------------------------------------------

Check you're not using a ModHeader-type browser extension to set the "Authorization" header. Avatar images are stored on AWS which requires an "Authorization" header when requesting files. The EHRI backend also requires an "Authorization" header and the two can conflict if a browser extension is being used to debug either one. 
