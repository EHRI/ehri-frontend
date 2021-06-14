xquery version "3.1";

declare namespace xquery="http://basex.org/modules/xquery";
declare namespace csv="http://basex.org/modules/csv";

(: make children for the given target path in the configuration :)
(: $target-path: the target path for which to make children as in the configuration :)
(: $source-node: the node (e.g. element) in the source document that corresponds to the given target path :)
(: $configuration: the parsed configuration file as a document node :)
(: $namespaces: map from namespace prefix to namespace URI :)
(: returns: a list of children nodes (attributes or elements) for the given target path :)
declare function local:make-children(
  $target-path as xs:string,
  $source-node as item(),
  $configuration as document-node(),
  $namespaces as map(xs:string, xs:string),
  $nsString as xs:string,
  $libURI as xs:anyURI,
  $count as xs:integer
) as node()* {

  (: go through the target nodes defined for this target path in order of configuration :)
  for $configuration-record at $pos in $configuration/csv/record[target-path/text() = $target-path]
    (: go through the source nodes corresponding to each target node :)
    (: let $f := trace($configuration-record, 'Record: ') :)
    for $child-source-node in local:evaluate-xquery($configuration-record/source-node/text(), $source-node, $libURI)
      let $child-value := local:evaluate-xquery($configuration-record/value/text(), $child-source-node, $libURI)
      let $child-name := $configuration-record/target-node/text()
      (: let $f := fn:trace("Child name: "|| $child-name || ", Value: " || $child-value || ", Child source node: " || $child-source-node || ", Source node: " || $source-node) :)
      return try {

        (: return an attribute :)
        if (fn:starts-with($child-name, "@")) then
          let $child-name := fn:substring($child-name, 2)
          let $name-prefix := fn:substring-before($child-name, ":")
          let $child-qname := if ($name-prefix) then fn:QName($namespaces($name-prefix), $child-name) else $child-name
          let $child := attribute { $child-qname } { $child-value }
          return if ($child-value) then $child else ()

        (: return an element :)
        else
          let $name-prefix := fn:substring-before($child-name, ":")
          let $child-qname := fn:QName($namespaces($name-prefix), $child-name)
          let $child-children := local:make-children(fn:concat($target-path, $child-name, "/"), $child-source-node, $configuration, $namespaces, $nsString, $libURI, $count + $pos)
          let $child := element { $child-qname } { $child-children, $child-value }
          return if ($child-children or local:ebv($child-value)) then $child else ()
      } catch * {
        fn:error(xs:QName("mapping-error"), "at " || $target-path || $child-name || ": " || $err:description)
      }
};

(: evaluate an XQuery expression within a given context node :)
(: $xquery: the XQuery expression to evalute as a string :)
(: $context: the node (e.g. element) to use as context for the XQuery expression :)
(: $libURI: the URI of a library containing external functions :)
(: returns: the list of atomic values or nodes that the XQuery expression evaluated to :)
declare function local:evaluate-xquery(
  $xquery as xs:string?,
  $context as item(),
  $libURI as xs:anyURI
) as item()* {
  if ($xquery) then
    if (fn:exists($libURI) and fn:contains($xquery, "xtra")) then
      xquery:eval("import module namespace xtra = ""xtra"" at """ || $libURI || """;" || $nsString || $xquery, map { "": $context })
    else if (fn:contains($xquery, ":")) then
      xquery:eval($nsString || $xquery, map {"": $context})
    else
      xquery:eval($xquery, map {"": $context})
  else ()
};

declare function local:ebv(
  $item as item()*
) as xs:boolean {
  if (fn:count($item) > 1) then
    let $ones := for $element in $item return if (local:ebv($element)) then 1 else ()
    return (fn:count($ones) > 0)
  else fn:boolean($item)
};
(: transform a source document into target documents with the given configuration and namespaces :)
(: $source-document: the source document as a single document node (if you need to transform multiple documents at the same time, you can wrap them in a single document node) :)
(: $configuration: the transformation configuration as a TSV string :)
(: $namespaces: map from namespace prefix to namespace URI :)
(: returns: a list of target document nodes :)
declare function local:transform(
  $source-document as document-node(),
  $configuration as xs:string,
  $namespaces as map(xs:string, xs:string),
  $nsString as xs:string,
  $libURI as xs:anyURI
) as document-node()* {
  let $configuration := csv:parse($configuration, map { "separator": "tab", "header": "yes", "quotes": "no" })
    for $target-root-node in local:make-children("/", $source-document, $configuration, $namespaces, $nsString, $libURI, 0)
    return document { $target-root-node }
};


declare variable $namespaces as map(xs:string, xs:string) external;
declare variable $mapping as xs:string external;
declare variable $input as xs:string external;
declare variable $nsString as xs:string external;
declare variable $libURI as xs:anyURI external;

let $source-document := fn:parse-xml($input)
for $target-document in local:transform($source-document, $mapping, $namespaces, $nsString, $libURI)
    return $target-document

