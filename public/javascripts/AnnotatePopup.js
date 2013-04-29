/*$('#annotation-popup').on('hidden', function () {
    window.location.href = "#";
});
*/

/*
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
*/
/**
 * Module for performing a search...
 */


!(function() {
  angular.module('AnnotatePopup', ["ngSanitize", 'ui.bootstrap' ], function($provide) {
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
        when('/search/:type', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',   controller: SearchCtrl}).
        otherwise({redirectTo: '/search/documentaryUnit'});
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
      var s = "link[" + idx + "].id=" + ele.id + "&" +
          "link[" + idx + "].data.category=associative&" +
          "link[" + idx + "].data.description=Test Annotation";
      args.push(s)
    });
    return $portal.saveAnnotations($scope.id, args).then(function(response) {
      $window.location = "/docs/show/" + $scope.id;
    });
  }

  $rootScope.removeSelected = function(id) {
    console.log("Remove: " + id)
    $scope.selected = $scope.selected.filter(function(ele, idx, arr) {
      return (ele.id !== id);
    });
  }
  
 $scope.editItem = function(item) {
    console.log("ReEditing: " + item);
	$rootScope.tempSelected = item;
	console.log($scope.tempSelected);
  }
  
}

function SearchCtrl($scope, $portal, $log, $rootScope, $routeParams) {
  $scope.id = $routeParams.id;
  $scope.item = null;
  $scope.itemData = null;
  $scope.type = $routeParams.type;
  $scope.results = [];
  $scope.paginationResults = []
  //Pagination system
  $scope.currentPage = 1;
  $scope.maxSize = 5;
  $scope.numPages = false;
 
  $scope.addTemp = function() {
    console.log("Editing: " + $scope.item);
    $rootScope.tempSelected = {
      id: $scope.itemData.id,
      type: $scope.itemData.type,
      name: $scope.itemData.relationships.describes[0].data.name
    };
	
    console.log($rootScope.tempSelected)
  }
 
  $scope.closeEdit = function() {
  $rootScope.tempSelected = null;
 }
 
 
  $scope.addSelected = function(item) {
    console.log($rootScope.tempSelected);
	//Adding Title and Desc
	$rootScope.tempSelected = item;
    $rootScope.tempSelected["linkTitle"] = $scope.linkTitle;
    $rootScope.tempSelected["linkDesc"] = $scope.linkDesc;
    $rootScope.tempSelected["linkType"] = $scope.linkType;
	
	//Cleaning Results
	$rootScope.removeSelected($rootScope.tempSelected.id);
	
	//Pushing to results
	$scope.selected.push($rootScope.tempSelected);
	
	//Cleaning Temp
	$rootScope.tempSelected = null;
    console.log($scope.selected);
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
	
      $scope.results = [];
      $scope.currentPage = response.data.page;
	  $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
      $scope.searching = false;
	  console.log($scope.currentPage);
    });
  }
  
  $scope.$watch("currentPage", function(newValue, oldValue) { 
		if(!$scope.results[newValue])
		{
			$scope.moreResults($scope.q);
		}
	});
  
  $scope.moreResults = function(searchTerm) {
    $scope.searching = true;
    return $portal.search($scope.type, searchTerm, $scope.currentPage).then(function(response) {
	
      // Append results instead of replacing them...
	  
      $scope.currentPage = response.data.page;
      $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
      $scope.searching = false;
	  
    });
  }
  
  $scope.doSearch("");
}