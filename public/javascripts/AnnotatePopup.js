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
          $log.log("Searching with: ", "/search/" + type + params)
          return $http.get("/filter/" + type + params);
        },

        detail: function(type, id) {
          return $http.get("/api/" + type + "/" + id);
        }

      };
    });
  }).config(['$routeProvider', function($routeProvider) {
    $routeProvider.
        when('/type', {templateUrl: ANGULAR_ROOT + '/partials/search-type.tpl.html',   controller: ItemTypeCtrl}).
        when('/search/:type', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',   controller: SearchCtrl}).
        when('/search/:type/:itemId', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html', controller: SearchCtrl}).
        otherwise({redirectTo: '/type'});
  }]);
}).call(this);


function ItemTypeCtrl($scope, $portal, $log, $rootScope, $routeParams) {

}

function ItemDetailCtrl($scope, $portal, $log, $rootScope, $routeParams) {
  $scope.id = $routeParams.itemId;
}


function SearchCtrl($scope, $portal, $log, $rootScope, $routeParams) {

  $scope.selected = [["foo", "Foobar", "Reason"]];
  $scope.item = null;
  $scope.itemData = null;
  $scope.type = $routeParams.type;
  $scope.numPages = false;
  $scope.results = [];
  $scope.currentPage = 1;

  $scope.addSelected = function() {
    console.log("Add: " + $scope.item)
    $scope.selected.push([$scope.item, $scope.itemData.relationships.describes[0].data.name])
  }

  $scope.removeSelected = function(id) {
    console.log("Remove: " + id)
    $scope.selected = $scope.selected.filter(function(ele, idx, arr) {
      return (ele[0] !== id);
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