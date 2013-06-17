var portal = angular.module('portalSearch', ['ui.bootstrap' ], function ($provide) {
    $provide.factory('$service', function() {
      return {
        redirectUrl: function(type, id) {
          return jsRoutes.controllers.Application.getType(type, id).url;
        }
      };
    });
  });


portal.
	config(['$routeProvider', function($routeProvider) {
	$routeProvider.
		when('/item/repository/:itemID', {templateUrl: ANGULAR_ROOT + '/search/repository.html', controller:"RepoCtrl", resolve: Repo.resolve}).
		when('/item/documentaryUnit/:itemID', {templateUrl: ANGULAR_ROOT + '/search/documentaryUnit.html', controller:"DocCtrl", resolve: Doc.resolve, reloadOnSearch: false}).
		when('/', {templateUrl: ANGULAR_ROOT + '/search/searchlikeg.html', controller:"SearchCtrl", reloadOnSearch: false}).
		otherwise({redirectTo: '/'});
}]);