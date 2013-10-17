// create directive module (or retrieve existing module)
var m = angular.module("Portal");

// create the "my-dir" directive
m.directive("searchItem", function() {
  return {
    restrict: "E",        // directive is an Element (not Attribute)
    scope: {              // set up directive's isolated scope
      item: "=",           // name var passed by value (string, one-way)
      type: "@"
    },
    replace: true,
    templateUrl: "/assets/partials/documentaryUnit/list.tpl.html"
  }
});