/**
 * Module for performing a search...
 */


!(function() {
  angular.module('AnnotatePopup', [ ], function($provide) {
    $provide.factory('$portal', function($http, $log) {
      return {
        search: function(searchTerm, page) {
          var params = "?q=" + (searchTerm || "");
          if (page) {
            params = params + "&page=" + page;
          }
          $log.log("Searching with: ", "/search" + params)
          return $http.get("/search" + params, {headers: {"Accept": "application/json"}});
        }
      };
    });
  }).config(['$routeProvider', function($routeProvider) {
    $routeProvider.
        when('/type', {templateUrl: ANGULAR_ROOT + '/partials/search-type.tpl.html',   controller: ItemTypeCtrl}).
        when('/search/:type', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',   controller: SearchCtrl}).
        when('/search/:itemId', {templateUrl: ANGULAR_ROOT + '/partials/item-detail.tpl.html', controller: ItemDetailCtrl}).
        otherwise({redirectTo: '/type'});
  }]);
}).call(this);


function ItemTypeCtrl($scope, $portal, $log, $rootScope, $routeParams) {

}

function ItemDetailCtrl($scope, $portal, $log, $rootScope, $routeParams) {
  $scope.id = $routeParams.itemId;
}


function SearchCtrl($scope, $portal, $log, $rootScope, $routeParams) {
  $scope.type = $routeParams.type;
  $scope.numPages = false;
  $scope.results = [];
  $scope.currentPage = 1;

  $scope.doSearch = function(searchTerm, page) {
    $scope.searching = true;
    return $portal.search(searchTerm, page).then(function(response) {
      $scope.results = response.data.results;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
      $scope.searching = false;
    });
  }

  $scope.moreResults = function(searchTerm) {
    $scope.searching = true;
    return $portal.search(searchTerm, $scope.currentPage + 1).then(function(response) {
      // Append results instead of replacing them...
      $scope.results.push.apply($scope.results, response.data.results);
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
      $scope.searching = false;
    });
  }
}