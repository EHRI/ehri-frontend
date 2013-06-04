var Repo = portal.controller('RepoCtrl', ['$scope', '$service', '$routeParams', 'Item', function($scope, $service, $routeParams, $item) {
	$scope.desc = $item.data.relationships.describes[0];
	$scope.address = $scope.desc.relationships.hasAddress[0].data;
	console.log($scope.address);
}]);

Repo.resolve = {
	itemData: function($route, Item) {
		var result = Item.query("repository", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 