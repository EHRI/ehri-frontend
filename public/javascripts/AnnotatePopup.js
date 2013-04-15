$('#annotation-popup').on('hidden', function () {
    window.location.href = "#";
});

$(document).ready(function () {
	//RegEXP
	var SearchCheck = new RegExp("^(#/search/)");
	var hash = window.location.hash;
	
	//State (Avoid further checking)
	var AnnotationLoaded = false;
	
	//If bookmarked, will show the popup
	if(hash.match(SearchCheck) && !AnnotationLoaded )
	{
		$('#annotation-popup').modal('show');
		var AnnotationLoaded = true;
	}
});
/**
 * Module for performing a search...
 */


!(function() {
  angular.module('AnnotatePopup', ["ngSanitize" ], function($provide) {
    $provide.factory('$portal', function($http, $log) {
      return {
        search: function(type, searchTerm, page) {
			  var params = "?q=" + (searchTerm || "");
			  if (page) {
				params = params + "&page=" + page;
			  }
			  // $log.log("Searching with: ", "/search/" + type + params)
			  return $http.get("/filter/" + type + params);
        },

        detail: function(type, id) {
          return $http.get("/api/" + type + "/" + id);
        },

        saveAnnotations: function(id, args) {
          var postArgs = {
            method: "POST",
            url: "/docs/linkm/" + id,
            data: args.join("&"),
            headers: {'Content-Type': 'application/x-www-form-urlencoded', 'ajax-ignore-csrf': true}
          };
          $log.log("Post args...", postArgs);
          return $http(postArgs);
        }

      };
    });
  }).config(['$routeProvider', function($routeProvider, $locationProvider) {
    $routeProvider.
        when('', {templateUrl: ANGULAR_ROOT + '/partials/search-type.tpl.html',   controller: ItemTypeCtrl}).
        when('/search/:type', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',   controller: SearchCtrl}).
        otherwise({redirectTo: ''});
  }]);
}).call(this);


function ItemTypeCtrl() {

}

function SaveCtrl($scope, $window, $portal, $log, $rootScope, $routeParams) {
  $scope.id = $window.ITEM_ID;
  console.log("Item id: " + $scope.id);
  $scope.selected = [];
  $scope.save = function() {
    var args = [];
    $scope.selected.forEach(function(ele, idx) {
      var s = "annotation[" + idx + "].id=" + ele.id + "&" +
          "annotation[" + idx + "].data.annotationType=link&" +
          "annotation[" + idx + "].data.body=Test Annotation";
      args.push(s)
    });
    return $portal.saveAnnotations($scope.id, args).then(function(response) {
      $window.location = "/docs/show/" + $scope.id;
    });
  }

  $scope.removeSelected = function(id) {
    console.log("Remove: " + id)
    $scope.selected = $scope.selected.filter(function(ele, idx, arr) {
      return (ele.id !== id);
    });
  }
}

function SearchCtrl($scope, $portal, $log, $rootScope, $routeParams) {
  $scope.id = $routeParams.id;
  $scope.item = null;
  $scope.itemData = null;
  $scope.type = $routeParams.type;
  $scope.numPages = false;
  $scope.results = [];
  $scope.currentPage = 1;

  $scope.addSelected = function() {
    console.log("Add: " + $scope.item)
    $scope.selected.push({
      id: $scope.itemData.id,
      type: $scope.itemData.type,
      name: $scope.itemData.relationships.describes[0].data.name
    });
  }

  $scope.hasMorePages = function() {
    return $scope.currentPage < $scope.numPages;
  }

  $scope.setItem = function(item) {
    if ($scope.item === item) {
      $scope.item = $scope.itemData = null;
    } else {
      $scope.item = item;
      $scope.loading = true;
      return $portal.detail($scope.type, item).then(function(response) {
        console.log(response.data);
        $scope.itemData = response.data;
        $scope.loading = false;
      });
    }
  }

  $scope.doSearch = function(searchTerm, page) {
    $scope.searching = true;
    return $portal.search($scope.type, searchTerm, page).then(function(response) {
      console.log(response.data)
      $scope.results = response.data.items;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
      $scope.searching = false;
    });
  }

  $scope.moreResults = function(searchTerm) {
    $scope.searching = true;
    return $portal.search($scope.type, searchTerm, $scope.currentPage + 1).then(function(response) {
      // Append results instead of replacing them...
      $scope.results.push.apply($scope.results, response.data.items);
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
      $scope.searching = false;
    });
  }
  $scope.doSearch("")
}