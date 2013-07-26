var Concept = portal.controller('ConceptCtrl', ['$scope', '$http', '$routeParams', 'Item', function($scope, $http, $routeParams, $item) {
	$scope.item = $item.data;
	console.log($scope.item);
	$scope.descs = $item.data.relationships.describes;
}]);

Concept.resolveConcept = {
	itemData: function($route, Item) {
		console.log($route.current.params.itemID);
		var result = Item.query("cvocConcept", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 