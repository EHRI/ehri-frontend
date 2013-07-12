var portal = angular.module('portalSearch', ['ui.bootstrap', 'ui.sortable' ], function ($provide) {
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
		when('/basket', {templateUrl: ANGULAR_ROOT + '/portal/view/basket.html', controller:"BasketCtrl"}).
		when('/item/repository/:itemID', {templateUrl: ANGULAR_ROOT + '/portal/view/repository.html', controller:"RepoCtrl", resolve: Repo.resolveRepo}).
		when('/item/documentaryUnit/:itemID', {templateUrl: ANGULAR_ROOT + '/portal/view/documentaryUnit.html', controller:"DocCtrl", resolve: Doc.resolveDoc, reloadOnSearch: false}).
		when('/', {templateUrl: ANGULAR_ROOT + '/portal/view/search.html', controller:"SearchCtrl", reloadOnSearch: false}).
		otherwise({redirectTo: '/'});
}]);