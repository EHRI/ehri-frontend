portal.controller('SearchCtrl', ['$scope', 'portal', '$http', '$routeParams', '$location', '$service', '$anchorScroll', 'myPaginationService', 'myBasketService', 'Map', function($scope, $portal, $http, $routeParams, $location, $service, $anchorScroll, paginationService, $basket, $map) {
	$scope.results = {	//Results object
		raw: {	//Raw Results
		},
		pages: {	//Results divided by page
			/*
			#id# : {
				id: string,
				items : {}
			}
			*/
		},
		loaded: {	//Loaded pages
		}
	};
	$scope.ui = {
		general: {	//General params, such as default language
			lang: "en",	//Default language for descriptions
			loading: false // If data are being loaded (previously loadingPage)
		},
		pagination : {
			available: false, //Number of available pages for said query (previously numPages)
			next: function() { //Previously loadMore
				console.log("Next page");
				var next = parseInt($scope.ui.search.params.page) + 1;
				// /*	
					//Debug
					console.log("Available =" +this.available);
					console.log("Next = " + next);
					console.log("Next loaded = " + (!$scope.results.loaded[next]));
					//End debug
				// */
				if(	$scope.ui.general.loading == false && //Check if not currently loading, avoiding multiple load
					next <= this.available &&	//Check if current page is not maximum
					(!$scope.results.loaded[next]))	{	//Check if not already loaded
					$scope.ui.general.loading = true;	//Set loading
					$scope.ui.search.params.page = next;	//Set param
					$scope.ui.search.load();	//Load data (!= New query)
					//console.log("ScrolltoCall for page " + $scope.currentPage);
				}
			}
		},
		search: {
			/*advanced : function (input) {	//Parse the query for advanced use of research
				//console.log("checking advanced us of input in "+input);
				var regexp = {
					type : /TYPE\(([A-z]+)\)/,
					sort : /SORT\(([A-z]+)\)/
				}
				//console.log(regexp);
				angular.forEach(regexp, function(value, key) {
					//console.log("Checking with regexp "+key+" for "+input);
					r = new RegExp(value);
					results = r.exec(input);
					if(results != null) {
						$scope.ui.search.filter.set(key, results[1]);
					}
				});
			},*/
			input : function () {	//Function triggered on ng-change for  searchTerm
				//this.advanced($scope.searchTerm);
				if(this.auto) {
					this.filter.set('q', $scope.searchTerm);
				} else {
					this.params.q = $scope.searchTerm;
				}
					
			},
			facets : {},
			auto: false,	//True : search while typing, false : need to press enter or submit button (previously noSearchOnChange)
			params : { q: "", sort: "score.desc", page:1}, // Original filters, page added as 1 (previously currentPage)
			get: function () {	// Function to get results for a new query
				var url = this.url();	//Get url with params
				$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {	//Get json
					//Erase every results data and put raw data results in it
					$scope.results = {	raw: data, pages: {},loaded: {	}};
					$scope.results.pages[$scope.ui.search.params.page] = { 
						id:	$scope.ui.search.params.page, 
						items : data.page.items 
					}
					// console.log(data.page.items);
					$scope.results.loaded[$scope.ui.search.params.page] = true;	//Set page as loaded
					/*= {	//Erase every data
						raw: data,	//Save raw results
						loaded: {	$scope.ui.search.params.page : true},	//Set given page as loaded
						pages: {
							$scope.ui.search.params.page : {
								id: $scope.ui.search.params.page.toString(),
								items: data.page.items
							}
						}
					}*/
					
					//Set facets
					$scope.ui.search.facets = data.page.facets;
					//Set number of available pages
					
					//Calculate the number of available pages
					var numPages = Math.ceil(data.page.total / data.page.limit);
					
					$scope.ui.pagination.available = numPages;
					//Stop loading
					$scope.ui.general.loading = false;
					//console.log($scope.results);
					
					//Refreshing url
					$scope.ui.url.set();
				});
				//Set the current page id on first child of scope.results.pages.X
				//$scope.results.pages[$scope.ui.search.params.page].items[0].page = $scope.ui.search.params.page;	
			},
			load: function() {	// Function for loading a page with same query
				var url = this.url('/search');	//Get url with params
					$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {	//Get json
					//Insert data
					$scope.results.raw = data;
					$scope.results.pages[$scope.ui.search.params.page] = {
						id: $scope.ui.search.params.page.toString(),
						items: data.page.items
					};

					//Stop loading
					$scope.ui.general.loading = false;
					//Set the current page id on first child of scope.results.pages.X
					//$scope.results.pages[$scope.ui.search.params.page].items[0].page = $scope.ui.search.params.page;	
					
					//Pagination
					$scope.results.loaded[$scope.ui.search.params.page] = true;
					//if(scroll)	{	$location.hash('page-' + $scope.currentPage);	$anchorScroll();	}	//Scroll to function
				});
			},
			filter : {
				set : function(key, value) { // Change a filter
					$scope.ui.search.params[key] = value;	//Set param
					$scope.ui.url.set();	//Set Url
					$scope.ui.search.get();	//Refresh data
					this.last = key;
				},
				remove : function(key) {	//Remove a filter
					delete $scope.ui.search.params[key];
					$scope.ui.url.set();	//Change url
					$scope.ui.search.get();	//Get the search
					//Need to update url
				},
				last : false //For debugging, if last filter causes bug				
			},
			url : function() {	//Set the API search url
				url = $portal.controllers.portal.Application.search().url;
				url = url + '?';	//Add question mark
				var urlArr = [];
				angular.forEach($scope.ui.search.params, function(value, key) {
					urlArr.push(key + "=" + value);
				});
				
				return url + urlArr.join('&');
			}
		},
		url : {	//Url for browser functions
			params : $location.search(),	//Search params in url
			set : function () { // Set search params in Url
				$location.search($scope.ui.search.params);
			},
			check: function () {	//Check if search data has been put in URL
				if(!isEmptyObject($location.search())) {	//If we have some datas in url for search
					this.get();
					$scope.ui.search.get();
				}
			},
			get: function () {	//Get search params through url
				this.params = $location.search();
				angular.forEach($scope.ui.url.params, function(value, key) {
					$scope.ui.search.params[key] = value;
					
					
					if(key == "q") { 	//We update the input
						$scope.searchTerm = value;
					}
				});
			}
		},
		basket : { //Basket functions
			add : function(item) {	//Add item to basket
				$basket.add(item);
			}
		},
		map : {
			state : false,
			activate : function() {
				this.state = true;
			},
			desactivate : function () {
				this.state = false;
			},
			toggle : function () {
				this.state = !this.state;
				if(this.state) { $map.broadcast.reset(); }
			},
			marker : {
				add : function(item, title, id) { $map.marker.add(item, title, id); },
				center : function(marker) { $map.marker.center(marker); }
			}
		}
	};
	
	
	/*
	For get and load, previous error system
	.error(function() { 
			$scope.removeFilterByKey($scope.lastFilter);
			//alert('Server error, reloading datas');
		}); 
	
	//Pagination System with outside pagination render
	$scope.$watch('currentPage + numPages', function(newValue) {
		//{'current' : 1, 'max': 10, 'num': false}
		paginationService.prepForBroadcast({'current' : $scope.currentPage, 'max': 5, 'num': $scope.numPages});
	});
	
    $scope.$on('handlePaginationBottomBroadcast', function() {
		var prev = $scope.currentPage;
        $scope.currentPage = paginationService.page;
		//console.log($scope.loadedPage);
		if($scope.loadedPage[$scope.currentPage])
		{
			$location.hash('page-' + $scope.currentPage);
			$anchorScroll();
		}
		else
		{
		
			$scope.searchParams.page = $scope.currentPage;	
			$scope.doSearch(true, (prev < $scope.currentPage));
			console.log("load new page");
		}
    });
	
	*/
	//$scope.fromSearch();
	//$scope.doSearch();
	$scope.ui.url.check();
}]);