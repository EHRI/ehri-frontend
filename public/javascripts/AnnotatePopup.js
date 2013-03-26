/**
 * Module for performing a search...
 */


!(function() {
  angular.module('AnnotatePopup', [ ], function($provide) {
    $provide.factory('$portal', function($http, $log) {
      return {
        search: function(searchTerm, page) {
          var params = "?q=" + searchTerm;
          if (page) {
            params = params + "&page=" + page;
          }
          $log.log("Searching with: ", "/search" + params)
          return $http.get("/search" + params, {headers: {"Accept": "application/json"}});
        }
      };
    });
  });
}).call(this);


function SearchController($scope, $portal, $log, $rootScope) {

  $scope.numPages = false;
  $scope.results = [];
  $scope.currentPage = 1;


  $scope.doSearch = function(searchTerm, page) {
    $log.log("SearchTerm: ", searchTerm, "Page: ", page);

    $scope.searching = true;

    return $portal.search(searchTerm, page).then(function(response) {
      $log.log("Results: ", response);
      $scope.results = response.data.results;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
    });
  }

  $scope.moreResults = function(searchTerm) {
    $log.log("SearchTerm: ", searchTerm);

    $scope.searching = true;

    return $portal.search(searchTerm, $scope.currentPage + 1).then(function(response) {
      $log.log("Results: ", response);
      $scope.results.push.apply($scope.results, response.data.results);
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
    });
  }
}