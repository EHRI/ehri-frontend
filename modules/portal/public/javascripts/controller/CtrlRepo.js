var Repo = portal.controller('RepoCtrl', ['$scope', '$service', '$http', '$routeParams', '$rootScope', 'Item', function($scope, $service, $http, $routeParams, $rootScope, $item) {
/*
	Object model :
	item {
		raw : Raw json data,
		descId : id of chosen description,
		desc : description,
		pictures: retrieve picture from wikimedia
	}
	ui.blocks { } //Block for UI
*/
	$scope.item = { 
		raw : $item.data, // Raw Datas
		id : $item.data.id, // Description
		desc : $item.data.descriptions[0], // Chosen description
		address : $item.data.descriptions[0].addresses[0], // Address
		geoloc : $item.geoloc,	//Geolocalisation data
		children : {	//Children of repository
			loading : true,	//Loading Status
			data : {},	//Data of children
			searchParams : { q: "", type: "score", order: "desc"},
			get : function(params) {
				$http.get('/api/repository/'+$scope.item.id+'/list?sort=' + $scope.item.children.searchParams.type + '.' + $scope.item.children.searchParams.order+ '&q=' + $scope.item.children.searchParams.q, {headers: {'Accept': "application/json"}}).success(function(data) {
					$scope.item.children.data = data;
					$scope.item.children.loading = false;
				}).error(function (data) {
					console.log("Error loading children json datas from repository");
				});
			},
			filter : function(key, value) {
				$scope.searchParams[type] = val;
				this.get();
			}
		} 
	};
	
	$scope.ui = { 
		blocks : {
			functions : {
				toggleHide : function (id) {
					if(id) {
						$scope.ui.blocks.list[id].hidden = !$scope.ui.blocks.list[id].hidden;
						$rootScope.$broadcast('ui.blocks.functions.toggleHide.'+id);
					} else {
						angular.forEach($scope.ui.blocks.list, function(value, key){
							$scope.ui.blocks.list[key].hidden = true;
							$rootScope.$broadcast('ui.blocks.functions.toggleHide.'+key);
						});
					}
				}, // End toggleHide
				toggleClose : function (id) {
					if(id) {
						$scope.ui.blocks.list[id].closed = !$scope.ui.blocks.list[id].closed;
						$rootScope.$broadcast('ui.blocks.functions.toggleClose.'+id);
					} else {
						console.log("toggleHide all");
						angular.forEach($scope.ui.blocks.list, function(value, key){
							$scope.ui.blocks.list[key].closed = true;
							$rootScope.$broadcast('ui.blocks.functions.toggleClose.'+key);
						});
					}
				} // End toggleHide
			},
			list : {}
		} 
	};
	
	//Getting data for children
	$scope.item.children.get();
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