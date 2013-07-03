var Repo = portal.controller('RepoCtrl', ['$scope', '$service', '$routeParams', 'Item', function($scope, $service, $routeParams, $item) {
	$scope.itemId = $item.data.id;
	console.log($item);
	$scope.desc = $item.data.relationships.describes[0];
	$scope.address = $scope.desc.relationships.hasAddress[0].data;
	$scope.geoloc = $item.geoloc;
	console.log($item.geoloc);
	//console.log($scope.address);
}]);

Repo.resolveRepo = {
	itemData: function($route, Item) {
		console.log($route.current.params.itemID);
		var result = Item.query("repository", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 