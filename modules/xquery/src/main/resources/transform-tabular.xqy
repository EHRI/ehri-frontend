xquery version "3.1" encoding "UTF-8";

declare namespace xquery="http://basex.org/modules/xquery";
declare namespace csv="http://basex.org/modules/csv";
import module namespace mapping = "mapping" at "mapping.xqm";

(: --- helpers -------------------------------------------------------------- :)

(: split a string on a literal (non-regex) separator :)
declare function local:split-str($s as xs:string, $sep as xs:string) as xs:string* {
  if ($sep = "") then $s
  else if (fn:contains($s, $sep))
    then (fn:substring-before($s, $sep), local:split-str(fn:substring-after($s, $sep), $sep))
    else $s
};

(: parse a comma-separated list of column names, trimming whitespace and blanks :)
declare function local:cols($s as xs:string?) as xs:string* {
  for $c in fn:tokenize(($s, "")[1], ",")
    let $t := fn:normalize-space($c)
    where $t ne ""
    return $t
};

(: the "effective boolean value", extended to treat empty maps/arrays as false :)
declare function local:ebv($item as item()*) as xs:boolean {
  if (fn:count($item) gt 1) then
    let $ones := for $e in $item return if (local:ebv($e)) then 1 else ()
    return fn:count($ones) gt 0
  else if ($item instance of map(*)) then map:size($item) gt 0
  else if ($item instance of array(*)) then array:size($item) gt 0
  else fn:boolean($item)
};

(: evaluate an XQuery expression against a record, exposing its columns as :)
(: `?column` lookups (plus xtra: functions), as in the other transformers. :)
(: If the input has duplicate column names, the first one wins. :)
declare function local:eval-row(
  $expr as xs:string?,
  $record as element(record),
  $libURI as xs:anyURI
) as item()* {
  if ($expr) then
    (: resolve duplicate column names to the FIRST entry, for consistency with the other ops below :)
    let $row := map:merge(
      (: columns named by position (with no input header) are looked up by number, e.g. ?1 :)
      for $e in $record/entry
      let $key := if ($e/@position) then xs:integer($e/@position) else $e/@name/fn:string()
      return map:entry($key, $e/fn:string()),
      map { "duplicates": "use-first" })
    return
      if (fn:exists($libURI) and fn:contains($expr, "xtra")) then
        mapping:eval("import module namespace xtra = ""xtra"" at """ || $libURI || """;" || $expr, $row)
      else
        mapping:eval($expr, $row)
  else ()
};

(: --- producing ops --------------------------------------------------------- :)
(: each reads directly from the original input record - never from another   :)
(: row's output - and returns the output entry/entries it contributes.       :)

(: the first input entry with the given column name (duplicate headers :)
(: otherwise silently concatenate, or error out of fn:string's 1-item limit) :)
declare function local:entry($record as element(record), $col as xs:string) as element(entry)? {
  $record/entry[@name = $col][1]
};

(: pass one input column through, optionally renamed :)
declare function local:produce-select($record as element(record), $col as xs:string, $to as xs:string?) as element(entry)* {
  let $name := ($to[. ne ""], $col)[1]
  let $e := local:entry($record, $col)
  return <entry name="{$name}">{ if ($e) then $e/node() else () }</entry>
};

(: join several input columns into one, skipping blank values :)
declare function local:produce-merge($record as element(record), $cols as xs:string*, $to as xs:string, $sep as xs:string) as element(entry)* {
  let $vals := for $c in $cols return fn:string(local:entry($record, $c))
  return <entry name="{$to}">{ fn:string-join($vals[. ne ""], $sep) }</entry>
};

(: split one input column into several fixed output columns :)
declare function local:produce-split($record as element(record), $col as xs:string, $into as xs:string*, $sep as xs:string) as element(entry)* {
  let $parts := local:split-str(fn:string(local:entry($record, $col)), $sep)
  let $n := fn:count($into)
  (: any parts beyond the number of target columns are kept in the last one :)
  for $i in 1 to $n
  let $value := if ($i eq $n) then fn:string-join($parts[position() ge $n], $sep) else $parts[$i]
  return <entry name="{$into[$i]}">{ $value }</entry>
};

(: compute one output column from an expression over the input record :)
declare function local:produce-derive($record as element(record), $to as xs:string, $expr as xs:string?, $libURI as xs:anyURI) as element(entry)* {
  <entry name="{$to}">{ fn:string-join(local:eval-row($expr, $record, $libURI) ! fn:string(.), "") }</entry>
};

(: dispatch one producing row against one input record :)
declare function local:produce(
  $op as element(record),
  $record as element(record),
  $arr-sep as xs:string,
  $libURI as xs:anyURI
) as element(entry)* {
  let $name := fn:normalize-space($op/op)
  let $cols := local:cols($op/columns)
  let $to := local:cols($op/to)
  let $expr := $op/expr/text()
  let $sep := ($expr, $arr-sep)[1]
  return try {
    switch ($name)
      case "select" return local:produce-select($record, $cols[1], $to[1])
      case "merge"  return local:produce-merge($record, $cols, $to[1], $sep)
      case "split"  return local:produce-split($record, $cols[1], $to, $sep)
      case "derive" return local:produce-derive($record, $to[1], $expr, $libURI)
      (: unreachable in practice: $op is only ever dispatched here for a known op name :)
      default return fn:error(xs:QName("mapping-error"), "Unknown op: " || $name)
  } catch * {
    fn:error(xs:QName("mapping-error"), $err:code || " in op '" || $name || "': " || $err:description)
  }
};

(: the output column name(s) a producing row is declared to contribute, :)
(: without evaluating it against any actual record :)
declare function local:targets($op as element(record)) as xs:string* {
  let $name := fn:normalize-space($op/op)
  let $cols := local:cols($op/columns)
  let $to := local:cols($op/to)
  return switch ($name)
    case "select" return ($to[1], $cols[1])[1]
    case "merge"  return $to[1]
    case "split"  return $to
    case "derive" return $to[1]
    default return ()
};

(: does this input record satisfy every `filter` row? (vacuously true if none) :)
declare function local:keeps(
  $record as element(record),
  $filters as element(record)*,
  $libURI as xs:anyURI
) as xs:boolean {
  every $f in $filters satisfies
    try {
      local:ebv(local:eval-row($f/expr/text(), $record, $libURI))
    } catch * {
      fn:error(xs:QName("mapping-error"), $err:code || " in op 'filter': " || $err:description)
    }
};

(: build the output record for one input record: the columns contributed by :)
(: each producing row, in row order. A column only appears if some row      :)
(: produced it - except that with NO producing rows at all, every input     :)
(: column passes through unchanged, so a pure delimiter/header "reflavour"  :)
(: needs no pipeline rows. :)
declare function local:build-record(
  $record as element(record),
  $producing as element(record)*,
  $arr-sep as xs:string,
  $libURI as xs:anyURI
) as element(record) {
  <record>{
    if (fn:empty($producing))
    then $record/entry
    else for $op in $producing return local:produce($op, $record, $arr-sep, $libURI)
  }</record>
};

(: --- main ----------------------------------------------------------------- :)

declare variable $input as xs:string external;
declare variable $config as xs:string external;
declare variable $options as map(xs:string, xs:string) external;
declare variable $libURI as xs:anyURI external;

let $in-sep := (map:get($options, "input.separator"), "comma")[1]
let $in-header := if (map:get($options, "input.header") = ("no", "false")) then "no" else "yes"
let $out-sep := (map:get($options, "output.separator"), $in-sep)[1]
let $out-header := if ((map:get($options, "output.header"), $in-header)[1] = ("no", "false")) then "no" else "yes"
let $arr-sep := (map:get($options, "array.separator"), "||")[1]

let $parsed := csv:parse($input, map { "separator": $in-sep, "header": $in-header, "format": "attributes" })/csv
(: without an input header, columns are named by their position: 1, 2, 3... :)
let $grid :=
  if ($in-header = "yes") then $parsed
  else <csv>{
    for $r in $parsed/record
    return <record>{
      for $e at $i in $r/entry return <entry name="{$i}" position="{$i}">{ $e/node() }</entry>
    }</record>
  }</csv>
let $ops := mapping:parse-tsv($config)/csv/record
let $known := ("select", "merge", "split", "derive", "filter")
let $unknown := $ops[fn:normalize-space(op) != "" and fn:not(fn:normalize-space(op) = $known)]

return
  if (fn:exists($unknown))
  then fn:error(xs:QName("mapping-error"), "Unknown op: " || fn:normalize-space($unknown[1]/op))
  else
    let $producing := $ops[fn:normalize-space(op) = ("select", "merge", "split", "derive")]
    let $all-targets := $producing ! local:targets(.)
    (: two rows producing the same output column is (almost) always a mistake - most :)
    (: often, listing several source columns across separate `merge` rows instead of :)
    (: as one comma-separated `columns` cell on a single row :)
    let $dupes := for $t in fn:distinct-values($all-targets) where fn:count($all-targets[. = $t]) gt 1 return $t
    return
      if (fn:exists($dupes))
      then fn:error(xs:QName("mapping-error"),
        "Column '" || $dupes[1] || "' is produced by more than one row. If you meant to combine " ||
        "several source columns, list them together in one row's 'columns' cell (comma-separated), " ||
        "e.g. merge / colA,colB / " || $dupes[1] || " / <separator> - rather than a separate row per column.")
      else
        (: a source column that isn't in the input would silently give empty values, :)
        (: so report it - typically a typo, or a header that doesn't match :)
        let $input-cols := fn:distinct-values($grid/record/entry/@name ! fn:string(.))
        let $missing :=
          if (fn:exists($grid/record))
          then (
            for $op in $ops[fn:normalize-space(op) = ("select", "merge", "split")]
            for $col in local:cols($op/columns)
            where fn:not($col = $input-cols)
            return map { "op": fn:normalize-space($op/op), "col": $col }
          )[1]
          else ()
        return if (fn:exists($missing))
        then fn:error(xs:QName("mapping-error"),
          "Column '" || $missing?col || "' in a '" || $missing?op || "' row is not in the input. " ||
          "Input columns: " || fn:string-join($input-cols, ", "))
        else
        let $filters := $ops[fn:normalize-space(op) = "filter"]
        let $result :=
          <csv>{
            for $r in $grid/record
            where local:keeps($r, $filters, $libURI)
            return local:build-record($r, $producing, $arr-sep, $libURI)
          }</csv>
        return csv:serialize($result, map { "separator": $out-sep, "header": $out-header, "format": "attributes" })
