var Site = angular.module('Portal', []);

function AppController ($scope, $rootScope, $http) {

  $http.get(jsRoutes.controllers.portal.Application.account().url).success(function(data) {
    $rootScope.account = data;
  });

  $http.get(jsRoutes.controllers.portal.Application.profile().url).success(function(data) {
    $rootScope.profile = data;
  });

  $http.get(jsRoutes.controllers.portal.Application.search().url).success(function(data) {
    $scope.search = data;
    console.log(data);
  });
}
