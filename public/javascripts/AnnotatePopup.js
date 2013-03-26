/**
 * Module for performing a search...
 */


!(function() {
  angular.module('AnnotatePopup', [ ], function($provide) {
    $provide.factory('$portal', function($http, $log) {
      return {
        search: function(searchTerm) {
          var params = "?q=" + searchTerm;
          $log.log("Searching with: ", "/search" + params)
          return $http.get("/search" + params, {headers: {"Accept": "application/json"}});
        }
      };
    });
  });
}).call(this);


function SearchController($scope, $portal, $log, $rootScope) {
  $scope.doSearch = function(searchTerm) {
    $log.log("SearchTerm: ", searchTerm);

    $scope.results = [];

    $scope.searching = true;

    return $portal.search(searchTerm).then(function(response) {
      $log.log("Results: ", response);
      $scope.results = response.data;

    });
  }
}