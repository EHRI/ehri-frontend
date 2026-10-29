xquery version "3.1" encoding "UTF-8";

declare namespace csv="http://basex.org/modules/csv";

declare variable $input as xs:string external;
declare variable $options as map(xs:string, xs:string) external;

let $sep := (map:get($options, "input.separator"), "comma")[1]
let $grid := csv:parse($input, map { "separator": $sep, "header": "no" })/csv
let $header := $grid/record[1]/entry ! fn:string(.)
return fn:serialize(array { $header }, map { "method": "json", "indent": "no" })
