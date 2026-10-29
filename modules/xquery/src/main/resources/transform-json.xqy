xquery version "3.1" encoding "UTF-8";

declare namespace xquery="http://basex.org/modules/xquery";
import module namespace mapping = "mapping" at "mapping.xqm";

(: evaluate an XQuery expression using the given item as the context item :)
(: $xquery: the XQuery expression to evaluate as a string :)
(: $context: the item (map, array, or atomic value) to use as context :)
(: $libURI: the URI of a library containing external functions :)
(: returns: the sequence of values that the expression evaluated to :)
declare function local:evaluate(
  $xquery as xs:string?,
  $context as item()*,
  $libURI as xs:anyURI
) as item()* {
  if ($xquery) then
    if (fn:exists($libURI) and fn:contains($xquery, "xtra")) then
      mapping:eval("import module namespace xtra = ""xtra"" at """ || $libURI || """;" || $xquery, $context)
    else
      mapping:eval($xquery, $context)
  else ()
};

(: compute the value for a record: the `value` expression evaluated against the :)
(: context, or the context itself if no `value` expression is given :)
declare function local:value(
  $record as element(record),
  $context as item()*,
  $libURI as xs:anyURI
) as item()* {
  let $expr := $record/value/text()
  return if ($expr) then local:evaluate($expr, $context, $libURI) else $context
};

(: does the configuration define any records for the given target path? :)
declare function local:has-children(
  $configuration as document-node(),
  $target-path as xs:string
) as xs:boolean {
  fn:exists($configuration/csv/record[target-path = $target-path])
};

(: the "effective boolean value", extended to treat empty maps/arrays as false :)
declare function local:ebv(
  $item as item()*
) as xs:boolean {
  if (fn:count($item) gt 1) then
    let $ones := for $element in $item return if (local:ebv($element)) then 1 else ()
    return fn:count($ones) gt 0
  else if ($item instance of map(*)) then map:size($item) gt 0
  else if ($item instance of array(*)) then array:size($item) gt 0
  else fn:boolean($item)
};

(: compute the JSON value (object, array, or scalar) produced by a single record :)
(: $record: the configuration record :)
(: $target-path: the target path the record belongs to :)
(: $source-node: the item in the source document corresponding to the target path :)
(: $configuration: the parsed configuration file as a document node :)
(: $libURI: the URI of a library containing external functions :)
(: returns: the value for the record, or the empty sequence if it produces nothing :)
declare function local:record-value(
  $record as element(record),
  $target-path as xs:string,
  $source-node as item()*,
  $configuration as document-node(),
  $libURI as xs:anyURI
) as item()* {
  let $key := $record/target-node/text()
  let $type := ($record/type/text(), "value")[1]
  let $selector := $record/source-node/text()
  (: the source selector drives cardinality; an empty selector means the current context :)
  let $sources := if ($selector) then local:evaluate($selector, $source-node, $libURI) else $source-node
  let $sub-path := fn:concat($target-path, $key, "/")
  return switch ($type)

    (: a nested JSON object :)
    case "object" return
      let $context := fn:head($sources)
      return if (fn:exists($context))
        then local:make-object($sub-path, $context, $configuration, $libURI)
        else ()

    (: a JSON array; elements are nested objects if the configuration defines :)
    (: children for the sub-path, otherwise scalar values :)
    case "array" return
      array {
        for $source in $sources
        return if (local:has-children($configuration, $sub-path))
          then local:make-object($sub-path, $source, $configuration, $libURI)
          else local:value($record, $source, $libURI)
      }

    (: a scalar value :)
    default return
      let $context := fn:head($sources)
      return if (fn:exists($context)) then local:value($record, $context, $libURI) else ()
};

(: build a single JSON object (map) for the given target path :)
(: $target-path: the target path for which to build an object as in the configuration :)
(: $source-node: the item in the source document that corresponds to the given target path :)
(: $configuration: the parsed configuration file as a document node :)
(: $libURI: the URI of a library containing external functions :)
(: returns: a map representing the JSON object for the given target path :)
declare function local:make-object(
  $target-path as xs:string,
  $source-node as item()*,
  $configuration as document-node(),
  $libURI as xs:anyURI
) as map(*) {
  map:merge(
    (: go through the records defined for this target path in order of configuration :)
    for $record in $configuration/csv/record[target-path = $target-path]
      let $key := $record/target-node/text()
      return try {
        let $value := local:record-value($record, $target-path, $source-node, $configuration, $libURI)
        (: prune keys whose value is empty/absent (incl. empty objects and arrays) :)
        return if (local:ebv($value)) then map:entry($key, $value) else ()
      } catch * {
        fn:error(xs:QName("mapping-error"), $err:code || " at " || $target-path || $key || ": " || $err:description)
      }
  )
};

declare variable $mapping as xs:string external;
declare variable $input as xs:string external;
declare variable $libURI as xs:anyURI external;

let $source := fn:parse-json($input)
let $configuration := mapping:parse-tsv($mapping)
let $root-records := $configuration/csv/record[target-path = "/"]
(: If the root is a single record whose target-node is the "[]" marker, the output :)
(: is that record's value directly (e.g. a bare array), rather than an object. :)
let $result :=
  if (fn:count($root-records) eq 1 and $root-records/target-node = "[]")
  then local:record-value($root-records, "/", $source, $configuration, $libURI)
  else local:make-object("/", $source, $configuration, $libURI)
(: Don't escape "/" as "\/", which newer versions of BaseX (unlike 8.5) do by default :)
return fn:serialize($result, map { "method": "json", "indent": "yes", "escape-solidus": "no" })
