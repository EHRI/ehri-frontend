var Concept = portal.controller('ConceptCtrl', ['$scope', 'ui', '$http', '$routeParams', 'Item', function($scope, $ui, $http, $routeParams, $item) {
	$scope.item = $item.data;
	$ui.title("Concept | "+$scope.item.descriptions[0].name);
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