xquery version "3.1" encoding "UTF-8";

(: module for additional functions to be used in mapping configurations :)

module namespace xtra = "xtra";

(: a test function to validate the functioning of the transform script :)
declare function xtra:ehri() as xs:string {
  "EHRI_xtra_func"
};

declare function xtra:normalize-date-interval(
  $date-interval as xs:string
) as xs:string? {  
  let $dates := fn:tokenize($date-interval, "-")
  return if (fn:count($dates) = 1) then xtra:normalize-date($dates[1])
    else if (fn:count($dates) = 2) then
      let $date-from := xtra:normalize-date($dates[1])
      let $date-to := xtra:normalize-date($dates[2])
      return if ($date-from and $date-to) then fn:concat($date-from, "/", $date-to) else ()
    else ()
};

declare function xtra:normalize-date(
  $date as xs:string
) as xs:string? {
  let $year-regex := "[12][890][0-9][0-9]"
  let $year := xtra:xtract-matches($date, $year-regex)
  let $year := if (fn:count($year) = 1) then $year else ()
  let $date := fn:replace($date, $year-regex, "")
  
  let $month-regex := "January|February|March|April|May|June|July|August|September|October|November|December"
  let $month := xtra:xtract-matches($date, $month-regex)
  let $month := if ($year and fn:count($month) = 1)
    then if ($month = "January") then "01"
      else if ($month = "February") then "02"
      else if ($month = "March") then "03"
      else if ($month = "April") then "04"
      else if ($month = "May") then "05"
      else if ($month = "June") then "06"
      else if ($month = "July") then "07"
      else if ($month = "August") then "08"
      else if ($month = "September") then "09"
      else if ($month = "October") then "10"
      else if ($month = "November") then "11"
      else if ($month = "December") then "12"
      else ()
    else ()
  let $date := fn:replace($date, $month-regex, "")
  
  let $day-regex := "[0-3][0-9]"
  let $day := xtra:xtract-matches($date, $day-regex)
  let $day := if ($month and fn:count($day) = 1) then $day else ()
  
  return fn:concat($year, $month, $day)
};

(: extract the parts of the given string that match the given regular expression :)
declare function xtra:xtract-matches(
  $string as xs:string,
  $pattern as xs:string
) as xs:string* {
  xtra:xtract-splits($string, fn:tokenize($string, $pattern))
};

(: extract the parts of the given string that separate the given tokens :)
declare function xtra:xtract-splits(
  $string as xs:string,
  $tokens as xs:string*
) as xs:string* {
  if (fn:count($tokens) > 1) then
    let $string := fn:substring($string, fn:string-length($tokens[1]) + 1)
    let $split := if (fn:string-length($tokens[2]) = 0) then $string else fn:substring-before($string, $tokens[2])
    
    let $string := fn:substring($string, fn:string-length($split) + 1)
    let $tokens := fn:subsequence($tokens, 2)
    return ($split, xtra:xtract-splits($string, $tokens))
  else ()
};

declare variable $xtra:langs := map {
  "Estonian": "est",
  "Cree": "cre",
  "Swati": "ssw",
  "Luxembourgish": "ltz",
  "Amharic": "amh",
  "Kikuyu": "kik",
  "Slovenian": "slv",
  "Chechen": "che",
  "Interlingua": "ina",
  "Kannada": "kan",
  "Hebrew": "heb",
  "Ganda": "lug",
  "Frisian": "fry",
  "Telugu": "tel",
  "Tajik": "tgk",
  "Mongolian": "mon",
  "Zulu": "zul",
  "Interlingue": "ile",
  "Southern Sotho": "sot",
  "Luba-Katanga": "lub",
  "Azerbaijani": "aze",
  "Tibetan": "bod",
  "Lingala": "lin",
  "Nepali": "nep",
  "Basque": "eus",
  "Kirghiz": "kir",
  "Lao": "lao",
  "Khmer": "khm",
  "Chinese": "zho",
  "Serbian": "srp",
  "Georgian": "kat",
  "Navajo": "nav",
  "Avaric": "ava",
  "Ossetian": "oss",
  "Finnish": "fin",
  "Hungarian": "hun",
  "Scottish Gaelic": "gla",
  "Avestan": "ave",
  "Danish": "dan",
  "Norwegian Nynorsk": "nno",
  "Inuktitut": "iku",
  "Cornish": "cor",
  "Kanuri": "kau",
  "Kwanyama": "kua",
  "Occitan": "oci",
  "Dutch": "nld",
  "Croatian": "hrv",
  "Rundi": "run",
  "Norwegian": "nor",
  "Moldavian": "mol",
  "Xhosa": "xho",
  "Latvian": "lav",
  "Esperanto": "epo",
  "Maltese": "mlt",
  "Thai": "tha",
  "Armenian": "hye",
  "Korean": "kor",
  "Sundanese": "sun",
  "Greenlandic": "kal",
  "Somali": "som",
  "Maori": "mri",
  "Marshallese": "mah",
  "Ndonga": "ndo",
  "Zhuang": "zha",
  "Gallegan": "glg",
  "Fijian": "fij",
  "Bosnian": "bos",
  "Bashkir": "bak",
  "Wolof": "wol",
  "Sardinian": "srd",
  "Nyanja": "nya",
  "Icelandic": "isl",
  "Divehi": "div",
  "Vietnamese": "vie",
  "Tswana": "tsn",
  "Tonga": "ton",
  "Venda": "ven",
  "Ojibwa": "oji",
  "Malayalam": "mal",
  "Lithuanian": "lit",
  "Uighur": "uig",
  "Tagalog": "tgl",
  "Greek": "ell",
  "Malagasy": "mlg",
  "Igbo": "ibo",
  "Corsican": "cos",
  "Irish": "gle",
  "Oromo": "orm",
  "Bihari": "bih",
  "Raeto-Romance": "roh",
  "Tamil": "tam",
  "Abkhazian": "abk",
  "Sindhi": "snd",
  "English": "eng",
  "Oriya": "ori",
  "Malay": "msa",
  "Haitian": "hat",
  "Welsh": "cym",
  "Tatar": "tat",
  "Russian": "rus",
  "Shona": "sna",
  "Norwegian Bokmål": "nob",
  "Czech": "ces",
  "Inupiaq": "ipk",
  "North Ndebele": "nde",
  "Yiddish": "yid",
  "Twi": "twi",
  "Kazakh": "kaz",
  "Ewe": "ewe",
  "Tigrinya": "tir",
  "Akan": "aka",
  "Sichuan Yi": "iii",
  "Japanese": "jpn",
  "Urdu": "urd",
  "Marathi": "mar",
  "Sanskrit": "san",
  "Volapük": "vol",
  "Tsonga": "tso",
  "Limburgish": "lim",
  "Italian": "ita",
  "Kongo": "kon",
  "Javanese": "jav",
  "Walloon": "wln",
  "Gujarati": "guj",
  "Bengali": "ben",
  "Pali": "pli",
  "Bulgarian": "bul",
  "Chuvash": "chv",
  "Assamese": "asm",
  "Samoan": "smo",
  "Hiri Motu": "hmo",
  "Aymara": "aym",
  "Burmese": "mya",
  "Kurdish": "kur",
  "Persian": "fas",
  "Afrikaans": "afr",
  "Nauru": "nau",
  "Hausa": "hau",
  "Turkmen": "tuk",
  "Church Slavic": "chu",
  "Swahili": "swa",
  "Breton": "bre",
  "Guarani": "grn",
  "Faroese": "fao",
  "Herero": "her",
  "Arabic": "ara",
  "Romanian": "ron",
  "Afar": "aar",
  "Turkish": "tur",
  "Spanish": "spa",
  "Manx": "glv",
  "Kinyarwanda": "kin",
  "South Ndebele": "nbl",
  "Belarusian": "bel",
  "Komi": "kom",
  "Hindi": "hin",
  "Chamorro": "cha",
  "Catalan": "cat",
  "Slovak": "slk",
  "Quechua": "que",
  "Polish": "pol",
  "Sinhalese": "sin",
  "Kashmiri": "kas",
  "Indonesian": "ind",
  "Yoruba": "yor",
  "Ido": "ido",
  "Ukrainian": "ukr",
  "Northern Sami": "sme",
  "Macedonian": "mkd",
  "Panjabi": "pan",
  "Pushto": "pus",
  "Fulah": "ful",
  "German": "deu",
  "Bambara": "bam",
  "Tahitian": "tah",
  "Uzbek": "uzb",
  "Aragonese": "arg",
  "Sango": "sag",
  "Bislama": "bis",
  "Albanian": "sqi",
  "Latin": "lat",
  "Portuguese": "por",
  "Swedish": "swe",
  "French": "fra",
  "Dzongkha": "dzo"
};

declare function xtra:language-name-to-code($name as xs:string) as xs:string {
  if (map:contains($xtra:langs, $name))
      then map:get($xtra:langs, $name)
      else $name
};
