var Repo = portal.controller('RepoCtrl', ['$scope', '$service', '$http', '$routeParams', 'Item', function($scope, $service, $http, $routeParams, $item) {
	$scope.itemId = $item.data.id;
	console.log($item);
	$scope.desc = $item.data.relationships.describes[0];
	$scope.address = $scope.desc.relationships.hasAddress[0].data;
	$scope.geoloc = $item.geoloc;
	console.log($item.geoloc);
	//console.log($scope.address);
	
	
	//http://10.88.12.4:9000/api/repository/gb-003348/list
	$scope.children = {}
	$scope.children.loading = true;
	$http.get('/api/repository/'+$scope.itemId+'/list', {headers: {'Accept': "application/json"}}).success(function(data) {
		console.log(data);
		$scope.children.data = data;
		$scope.children.loading = false;
	}).error(function (data) {
		console.log("Error loading children json datas from repository");
	});
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