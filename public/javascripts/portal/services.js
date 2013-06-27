portal.factory('myPaginationService', function($rootScope) {
    var paginationService = {};
    
    paginationService.paginationDatas = {'current' : 1, 'max': 5, 'num': false};

    paginationService.prepForBroadcast = function(msg) {
        this.paginationDatas = msg;
        this.broadcastItem();
    };

    paginationService.broadcastItem = function() {
        $rootScope.$broadcast('handlePaginationBroadcast');
    };
	
    paginationService.changePage = function(page) {
        this.page = page;
        this.broadcastBottomItem();
    };

    paginationService.broadcastBottomItem = function() {
        $rootScope.$broadcast('handlePaginationBottomBroadcast');
    };
    return paginationService;
}).factory('myBasketService', function($rootScope) {
    var basketservice = {};
    
    basketservice.toBasket = {};

    basketservice.add = function(item) {
		// console.log(this);
		this.toBasket = item;
        //this.list.push(item);
        this.broadcastBasket();
    };
	
    basketservice.broadcastBasket = function() {
        $rootScope.$broadcast('handleBasketBroadcast');
    };
	
    return basketservice;
}).factory('Item', function($http){
	var Item = {}
	
	Item.query = function(type, item) {
		console.log(type);
		return $http.get('./api/'+type+'/'+item).success(function(data) {
			Item.data = data;
			if(type == "repository" && (data.relationships.describes[0].relationships.hasAddress[0]))
			{
				console.log("Getting Address");
				address = data.relationships.describes[0].relationships.hasAddress[0].data;
			/*
				format=[html|xml|json]
				street=<housenumber> <streetname>
				city=<city>
				county=<county>
				state=<state>
				country=<country>
				postalcode=<postalcode>
			*/
				$http.get('http://nominatim.openstreetmap.org/search/?format=json&street='+address.street+'&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
					if(data[0])
					{
						Item.geoloc = data[0];
						console.log(Item.address);
					}
				});
			}
		});
	}
	
	return Item;
}); 