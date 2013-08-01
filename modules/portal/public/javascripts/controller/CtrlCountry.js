var Country = portal.controller('CountryCtrl', ['$scope', 'Item', '$http', function($scope, $item, $http) {
	$scope.item = $item.data;
	
	$scope.children = {}
	$scope.children.loading = true;
	$http.get('/api/country/'+$scope.item.id+'/list', {headers: {'Accept': "application/json"}}).success(function(data) {
		console.log(data);
		$scope.children.data = data;
		$scope.children.loading = false;
	}).error(function (data) {
		console.log("Error loading children json datas from country");
	});
}]);

Country.resolveCountry = {
	itemData: function($route, Item) {
		console.log($route.current.params.itemID);
		var result = Item.query("country", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 