var Portal = angular.module('Portal', []).service("$search", [SearchService]);

Portal.config(['$routeProvider', function($routeProvider) {
  $routeProvider
      .when("/profile", {
        templateUrl: ANGULAR_PARTIALS + "/profile.tpl.html",
        controller: ProfileCtrl
      })
      .when("/search", {
        templateUrl: ANGULAR_PARTIALS + "/search.tpl.html",
        controller: SearchCtrl
      })
      .when("/item/:itemType/:itemId", {
        templateUrl: ANGULAR_PARTIALS + "/item.tpl.html",
        controller: ItemCtrl
      })
      .otherwise({redirectTo: "/search"});
}]).config(['$locationProvider', function($locationProvider) {
  $locationProvider.html5Mode(false);
  $locationProvider.hashPrefix = "/portal";
}]);


function AppCtrl ($scope, $rootScope, $http) {

  $rootScope.EntityTypes = EntityTypes;

  $http.get(jsRoutes.controllers.portal.Portal.account().url).success(function(data) {
    $rootScope.account = data;
  });

  $http.get(jsRoutes.controllers.portal.Portal.profile().url).success(function(data) {
    $rootScope.profile = data;
  });
}
