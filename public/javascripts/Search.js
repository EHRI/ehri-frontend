angular.module('portalSearch', []).
	config(['$routeProvider', function($routeProvider) {
	$routeProvider.
		when('/', {templateUrl: ANGULAR_ROOT + '/search/search.hml', controller: SearchCtrl}).
		when('/:searchTerm', {templateUrl: ANGULAR_ROOT + '/search/search.hml', controller: SearchCtrl}).
		when('/:searchTerm/:lang/', {templateUrl: ANGULAR_ROOT + '/search/search.hml', controller: SearchCtrl}).
		when('/:searchTerm/:lang/:type', {templateUrl: ANGULAR_ROOT + '/search/search.hml', controller: SearchCtrl}).
		otherwise({redirectTo: '/'});
}]);

function SearchCtrl($scope) {
	$scope.initiate = function () { 
		$http.get(ANGULAR_ROOT + '/search/mock-results.json').success(function(data) {
			console.log(data);
			$scope.page = data.page;
		});
	}
}