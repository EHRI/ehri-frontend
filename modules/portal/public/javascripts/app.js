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
		when('/basket', {templateUrl: ANGULAR_ROOT + '/view/controller-view/basket.html', controller:"BasketCtrl"}).
		when('/item/repository/:itemID', {templateUrl: ANGULAR_ROOT + '/view/controller-view/repository.html', controller:"RepoCtrl", resolve: Repo.resolveRepo}).
		when('/item/cvocConcept/:itemID', {templateUrl: ANGULAR_ROOT + '/view/controller-view/concept.html', controller:"ConceptCtrl", resolve: Concept.resolveConcept}).
		when('/item/country/:itemID', {templateUrl: ANGULAR_ROOT + '/view/controller-view/country.html', controller:"CountryCtrl", resolve: Country.resolveCountry}).
		when('/item/documentaryUnit/:itemID', {templateUrl: ANGULAR_ROOT + '/view/controller-view/documentaryUnit.html', controller:"DocCtrl", resolve: Doc.resolveDoc, reloadOnSearch: false}).
		when('/item/historicalAgent/:itemID', {templateUrl: ANGULAR_ROOT + '/view/controller-view/historicalAgent.html', controller:"AgentCtrl", resolve: Agent.resolveAgent, reloadOnSearch: false}).
		when('/search', {templateUrl: ANGULAR_ROOT + '/view/controller-view/search.html', controller:"SearchCtrl", reloadOnSearch: false}).
		when('/', {templateUrl: ANGULAR_ROOT + '/view/controller-view/search.html', controller:"SearchCtrl", reloadOnSearch: false}).
		otherwise({redirectTo: '/'});
}]);