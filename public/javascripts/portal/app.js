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
		when('/', {templateUrl: ANGULAR_ROOT + '/search/searchlikeg.html', controller:"SearchCtrl", reloadOnSearch: false}).
		otherwise({redirectTo: '/'});
}]);