var Doc = portal.controller('DocCtrl', ['$scope', 'ui', '$filter', '$location', '$http', 'Item', '$rootScope', function($scope, $ui, $filter, $location, $http, $item, $rootScope) {
	console.log($item);
	$scope.item = { 
		raw : $item.data, // Raw Datas
		id : $item.data.id, // Item ID
		desc : {
			id : false,
			data : {},
			load : function() { //Load desc depending on chosen desc (scope.desc.id) OR on language prority through filter.descLang
				if(this.id)	{ 
					this.data = $item.format($filter("descLang")($scope.item.raw.descriptions, false, {"id" : $scope.item.desc.id})[0]);
				} else {
					this.data = $item.format($filter("descLang")($scope.item.raw.descriptions)[0]);
				}
				$ui.title("Collection | "+ this.data.name);
			},
			set : function(descId) { // Set description id
				$scope.ui.url.params.description = descId; // Set url params
				$scope.ui.url.set();	// Apply it to url
			}
		} //Description
	}

	
	$scope.ui = { 
		blocks : {
			functions : {
				//Hide all blocks
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
				//Close all blocks
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
		},
		search: {	//Quick Search Module
			results : {},// Results object
			params : { q: "", type: "score", order: "desc"}, // Original filters
			get: function () {	// Function to get results
				query = '?sort=' + $scope.ui.search.params.type + '.' + $scope.ui.search.params.order+ '&q=' + $scope.ui.search.params.q;
				$http.get($item.search("documentaryUnit").url + query, $item.search("documentaryUnit").headers).success(function(data) {
					$scope.ui.search.results = data.page.items;
				});
			},
			filter : function(key, value) { // Change a filter
				$scope.searchParams[type] = val;
				this.get();
			}
		},
		url : {
			params : $location.search(), //Url parameters Search (?YOURITEM)
			resetCompared : function() {// Reset compared array in params using compared.data object loop
				this.params.compared = [];
				angular.forEach($scope.ui.compare.data, function(value, key) {
					$scope.ui.url.params.compared.push(key);
				});
				this.set();
			},
			set : function () { // Set search params in Url
				$location.search(this.params);
			},
			get : function() { // Set Compared items AND desc.id if in URL
				this.params = $location.search();
				
				if(typeof this.params.compared == "string") { // If we get a string instead of an array for compared item
					this.params.compared = this.params.compared.split(",");	//Split the results
				}
				
				if(this.params.description) { // If description has been submit through search url param
					$scope.item.desc.id = this.params.description;
					$scope.item.desc.load(); 
				}
				if (this.params.compared) { //If we got compared params, we erase previous loaded stuff and load their desc
					$scope.ui.compare.data = {};
					angular.forEach(this.params.compared, function(value, key) {
						$scope.ui.compare.load(value);
					});
				}
			} // End setFromUrl
		},
		compare : { // Compare part of UI
			data: {}, //Compared data
			load: function(itemId) { // Load a description for a compared item
				if(!$scope.ui.compare.data[itemId])	{
					$http.get($item.get("documentaryUnit", itemId).url, $item.get("documentaryUnit", itemId).headers).success(function(data) {
						$scope.ui.compare.data[itemId] = data;
						angular.forEach($scope.ui.compare.data[itemId].descriptions, function(value, key) {
							console.log(value);
							$scope.ui.compare.data[itemId].descriptions[key] = $item.format(value);
						});
						$scope.ui.compare.data[itemId].color = $scope.ui.compare.color();
						$scope.ui.url.resetCompared();
					});
				}
			},
			remove: function (descId) {
				delete this.data[descId];
				$scope.ui.url.resetCompared();
			},
			amount: 0, //Used for color
			color:	function () { //Return alternative color
				var available = ['deepblue', 'green', 'purple', 'alizarin']	// Available color
				if(this.amount  <= (parseInt(available.length) - 1)) {
					this.amount = this.amount+ 1;
				} else {
					this.amount = 0;
				}
				return available[this.amount - 1];
			}
		}
	};
	
	//Watch url changes
	/*
	$scope.$on('$routeUpdate', function(){
		$scope.ui.url.get();
	});
	*/
	
	//Launch Page
	$scope.item.desc.load();
	$scope.ui.url.get();
	
}]);

//Load data before UI
Doc.resolveDoc = {
	itemData: function($route, Item) {
		var result = Item.query("documentaryUnit", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 