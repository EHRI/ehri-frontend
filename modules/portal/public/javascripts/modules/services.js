portal.factory("portal", function() {
	var portal = {
		item : {
			documentaryUnit : {
				get : function(k) { return jsRoutes.controllers.archdesc.DocumentaryUnits.get(k).url;},
				search : function() { return jsRoutes.controllers.archdesc.DocumentaryUnits.search().url;}
			},
			repository : {
				get : function(k) { return jsRoutes.controllers.archdesc.Repositories.get(k).url; },
				search : function() { return jsRoutes.controllers.archdesc.Repositories.search().url; }
			},
			all : {
				search : function() { console.log(jsRoutes.controllers.portal.Application.search().url); return jsRoutes.controllers.portal.Application.search().url;}
			}
		}
	}
	return portal;
}).factory('myPaginationService', function($rootScope) {
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
    var basketservice = {
		content : {
			raw : [],
			transit : {}
		},
		add : function(item) {
			// console.log(this);
			this.content.transit = item;
			this.content.raw.push({id : item.id, type: item.type});
			//this.list.push(item);
			this.broadcast();
		},
		get : function() {
			return this.content.raw;
		},
		broadcast : function() {
			$rootScope.$broadcast('ui.basket.get');
		}
	};   
	
    return basketservice;
}).factory('Item', function($http, portal){
	
	var Item = {
		query : function(type, item, returned) {
			//console.log(type);
			return $http.get(portal.item[type].get(item), {headers: {'Accept': "application/json"}}).success(function(data) {
				if(returned) {
					return data;
				}
				else {
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
				}
			});
		},
		format : function(desc) {
			var identity = {};
			if(desc.dates && desc.dates.length > 0) { identity.dates = desc.dates }
			if(desc.extentAndMedium) { identity.extentAndMedium = desc.extentAndMedium }
			if(desc.levelOfDescription) { identity.levelOfDescription = desc.levelOfDescription }
			desc.identity = identity;
			return desc;
		},
		search : function(type) {
			return {url: portal.item[type].search(), headers : {headers: {'Accept': "application/json"}}};
		},
		get : function(type, key) {
			return {url: portal.item[type].get(key), headers : {headers: {'Accept': "application/json"}}};
		}
	}
	
	return Item;
}).factory('Map', function($http, $rootScope){
	var Map = {
		location : {	//Geolocation and location functions
			get: function () {	//Get geolocation of user through navigator, 
				try {
					if (typeof navigator.geolocation === 'undefined'){
						gl = google.gears.factory.create('beta.geolocation');
					} else {
						gl = navigator.geolocation;
					}
				} catch(e) {}

				if (gl) {
					gl.getCurrentPosition(Map.location.set, Map.error);
				} else {
					alert("Geolocation services are not supported by your web browser.");
				}
			},
			set: function(position) {	//Broadcast said position previously getPosition
				//console.log(position);
				$rootScope.position = position;
				Map.broadcast.center();
			}
		},
		position : {	//Raw data for position
		},
		broadcast : {
			reset : function() {
				$rootScope.$broadcast('ui.map.reset');
			},
			location : function() {
				$rootScope.$broadcast('ui.map.location');
			},
			center : function () {	//Broadcast map center event
				$rootScope.$broadcast('ui.map.marker.center');
			},
			marker : {
				add : function() {	//PReviously broadcastNewMapMarker
					$rootScope.$broadcast('ui.map.marker.new');
				}
			}
		},
		error : function() {
			console.log(map.position);
		},
		marker : {
			center : function(data) {	//Center map on {lat: data.lat, lng: data.lon} through broadcast
				$rootScope.mapCenter = {lat: data.lat, lng: data.lng};
				Map.broadcast.center();
			},
			add : function (data, title, id) {
				if(data[0])	{	//If we actually got data
					$rootScope.map.markers = {lat: data[0].lat, lng: data[0].lon, title: title, id:id};
					Map.ui.broadcast.marker.add();
				}
			},
			query : function(item, title, id) {	//Query API for lon and lat
				if(item.relationships.describes[0].relationships.hasAddress)
				{
					address = item.relationships.describes[0].relationships.hasAddress[0].data;
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
						if(data[0])	{
							Map.marked.add (data, title, id);
						} else {
							$http.get('http://nominatim.openstreetmap.org/search/?format=json&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
								if(data[0])	{
									Map.marked.add (data, title, id);
								}
							});
						}
						
					});
				}
			},
			temp : {}
		}
	}
	
	//Get location of users
	Map.location.get();
	
	return Map;
}); 