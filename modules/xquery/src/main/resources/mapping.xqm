xquery version "3.1" encoding "UTF-8";

(: Helpers shared by the transformation scripts. These are trusted code, and :)
(: not for use in mapping expressions (see xtra.xqm for those). :)

module namespace mapping = "mapping";

declare namespace xquery = "http://basex.org/modules/xquery";

(: Evaluate a mapping expression with the given context. Mapping expressions are :)
(: user input, so they are evaluated with no permissions: BaseX then refuses any :)
(: function that can access files, the network, the environment, other queries, :)
(: or Java. :)
declare function mapping:eval($query as xs:string, $context as item()*) as item()* {
  xquery:eval($query, map { "": $context }, map { "permission": "none" })
};

(: Parse a tab-separated mapping into the same structure as csv:parse with a header. :)
(: The fields are taken as-is: csv:parse can't be used since, even with quotes off, :)
(: BaseX 12 still turns "" into ", which breaks expressions containing "". As with :)
(: csv:parse, a leading byte order mark is ignored, and header names are made into :)
(: valid element names. Columns with an empty header are ignored. :)
declare function mapping:parse-tsv($tsv as xs:string) as document-node() {
  let $text := if (fn:starts-with($tsv, fn:codepoints-to-string(65279))) then fn:substring($tsv, 2) else $tsv
  let $lines := fn:tokenize($text, "\r?\n")[. ne ""]
  let $names := fn:tokenize(fn:head($lines), "\t") ! mapping:element-name(.)
  return document {
    <csv>{
      for $line in fn:tail($lines)
      let $fields := fn:tokenize($line, "\t")
      return <record>{
        for $name at $i in $names
        where $name ne ""
        return element { $name } { $fields[$i] }
      }</record>
    }</csv>
  }
};

(: A valid element name for a header, or the empty string for an empty header. :)
declare %private function mapping:element-name($header as xs:string) as xs:string {
  let $name := fn:replace(fn:normalize-space($header), "[^A-Za-z0-9_.\-]", "_")
  return if ($name eq "" or fn:matches($name, "^[A-Za-z_]")) then $name else "_" || $name
};
