/**
 *  Rheem EcoNet (Connect)
 *
 *  Copyright 2017 Justin Huff
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Last Updated : 1/1/17
 *
 *  Based on https://github.com/copy-ninja/SmartThings_RheemEcoNet
 *
 */
definition(
    name: "Rheem EcoNet (Connect)",
    namespace: "jjhuff",
    author: "Justin Huff",
    description: "Connect to Rheem EcoNet",
    category: "SmartThings Labs",
    iconUrl: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@1x.png",
    iconX2Url: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@2x.png",
    iconX3Url: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@3x.png")


preferences {
	page(name: "prefLogIn", title: "Rheem EcoNet")    
	page(name: "prefListDevice", title: "Rheem EcoNet")
}

/* Preferences */
def prefLogIn() {
	def showUninstall = username != null && password != null 
	return dynamicPage(name: "prefLogIn", title: "Connect to Rheem EcoNet", nextPage:"prefListDevice", uninstall:showUninstall, install: false) {
		section("Login Credentials"){
			input("username", "email", title: "Username", description: "Rheem EcoNet Email")
			input("password", "password", title: "Password", description: "Rheem EcoNet password (case sensitive)")
		} 
		section("Advanced Options"){
			input(name: "polling", title: "Server Polling (in Minutes)", type: "int", description: "in minutes", defaultValue: "5" )
		}
	}
}

def prefListDevice() {	
	if (login()) {
		def waterHeaterList = getWaterHeaterList()
		if (waterHeaterList) {
			return dynamicPage(name: "prefListDevice",  title: "Devices", install:true, uninstall:true) {
				section("Select which water heater to use"){
					input(name: "waterheater", type: "enum", required:false, multiple:true, metadata:[values:waterHeaterList])
				}
			}
		} else {
			return dynamicPage(name: "prefListDevice",  title: "Error!", install:false, uninstall:true) {
				section(""){ paragraph "Could not find any devices"  }
			}
		}
	} else {
		return dynamicPage(name: "prefListDevice",  title: "Error!", install:false, uninstall:true) {
			section(""){ paragraph "The username or password you entered is incorrect. Try again. " }
		}  
	}
}


/* Initialization */
def installed() { initialize() }
def updated() { 
	unsubscribe()
	initialize() 
}
def uninstalled() {
	unschedule()
    unsubscribe()
	getAllChildDevices().each { deleteChildDevice(it) }
}	

def initialize() {
	// Set initial states
	state.polling = [ last: 0, rescheduler: now() ]  
	    
	// Create selected devices
	def waterHeaterList = getWaterHeaterList()
    def selectedDevices = [] + getSelectedDevices("waterheater")
    selectedDevices.each {
    	def dev = getChildDevice(it)
        def name  = waterHeaterList[it]
        if (dev == null) {
	        try {
    			addChildDevice("jjhuff", "Rheem Econet Water Heater", it, null, ["name": "Rheem Econet: " + name])
    	    } catch (e)	{
				log.debug "addChildDevice Error: $e"
          	}
        }
    }
    
	// Remove unselected devices
	/*def deleteDevices = (selectedDevices) ? (getChildDevices().findAll { !selectedDevices.contains(it.deviceNetworkId) }) : getAllChildDevices()
	deleteDevices.each { deleteChildDevice(it.deviceNetworkId) } */
	
	//Subscribes to sunrise and sunset event to trigger refreshes
	subscribe(location, "sunrise", runRefresh)
	subscribe(location, "sunset", runRefresh)
	subscribe(location, "mode", runRefresh)
	subscribe(location, "sunriseTime", runRefresh)
	subscribe(location, "sunsetTime", runRefresh)
	    
	//Refresh devices
	runRefresh()
}

def getSelectedDevices( settingsName ) {
	def selectedDevices = []
	(!settings.get(settingsName))?:((settings.get(settingsName)?.getAt(0)?.size() > 1)  ? settings.get(settingsName)?.each { selectedDevices.add(it) } : selectedDevices.add(settings.get(settingsName)))
	return selectedDevices
}


/* Data Management */
// Listing all the water heaters you have in Rheem EcoNet
private getWaterHeaterList() { 	 
	def deviceList = [:]
	apiGet("/locations", [] ) { response ->
    	if (response.status == 200) {
          	response.data.equipment[0].each { 
            	if (it.type.equals("Water Heater")) {
                	deviceList["" + it.id]= it.name
                }
            }
        }
    }
    return deviceList
}

// Refresh data
def refresh() {
	if (!login()) {
    	return
    }
    
	log.info "Refreshing data..."
    // update last refresh
	state.polling?.last = now()

	// get all the children and send updates
	getAllChildDevices().each {
    	def id = it.deviceNetworkId
    	apiGet("/equipment/$id", [] ) { response ->
    		if (response.status == 200) {
            	log.debug "Got data: $response.data"
            	it.updateDeviceData(response.data)
            }
        }

    }
    
	//schedule the rescheduler to schedule refresh ;)
	if ((state.polling?.rescheduler?:0) + 2400000 < now()) {
		log.info "Scheduling Auto Rescheduler.."
		runEvery30Minutes(runRefresh)
		state.polling?.rescheduler = now()
	}
}

// Schedule refresh
def runRefresh(evt) {
	log.info "Last refresh was "  + ((now() - state.polling?.last?:0)/60000) + " minutes ago"
	// Reschedule if  didn't update for more than 5 minutes plus specified polling
	if ((((state.polling?.last?:0) + (((settings.polling?.toInteger()?:1>0)?:1) * 60000) + 300000) < now()) && canSchedule()) {
		log.info "Scheduling Auto Refresh.."
		schedule("* */" + ((settings.polling?.toInteger()?:1>0)?:1) + " * * * ?", refresh)
	}
    
	// Force Refresh NOWWW!!!!
	refresh()
    
	//Update rescheduler's last run
	if (!evt) state.polling?.rescheduler = now()
}

def setDeviceSetPoint(childDevice, setpoint) { 
	log.info "setDeviceSetPoint: $childDevice.deviceNetworkId $setpoint" 
	if (login()) {
    	apiPut("/equipment/$childDevice.deviceNetworkId", [
        	body: [
                setPoint: setpoint,
            ]
        ])
    }

}
def setDeviceEnabled(childDevice, enabled) {
	log.info "setDeviceEnabled: $childDevice.deviceNetworkId $enabled" 
	if (login()) {
    	apiPut("/equipment/$childDevice.deviceNetworkId", [
        	body: [
                isEnabled: enabled,
            ]
        ])
    }
}

private login() {
	def apiParams = [
    	uri: getApiURL(),
        path: "/auth/token",
        headers: ["Authorization": "Basic Y29tLnJoZWVtLmVjb25ldF9hcGk6c3RhYmxla2VybmVs"],
        requestContentType: "application/x-www-form-urlencoded",
        body: [
        	username: settings.username,
        	password: settings.password,
        	"grant_type": "password"
        ],
    ]
    if (state.session?.expiration < now()) {
    	try {
			httpPost(apiParams) { response -> 
            	if (response.status == 200) {
                	log.debug "Login good!"
                	state.session = [ 
                    	accessToken: response.data.access_token,
                    	refreshToken: response.data.refresh_token,
                    	expiration: now() + 150000
                	]
                	return true
            	} else {
                	return false
            	} 	
        	}
		}	catch (e)	{
			log.debug "API Error: $e"
        	return false
		}
	} else { 
    	// TODO: do a refresh 
		return true
	}
}

/* API Management */
// HTTP GET call
private apiGet(apiPath, apiParams = [], callback = {}) {	
	// set up parameters
	apiParams = [ 
		uri: getApiURL(),
		path: apiPath,
        headers: ["Authorization": getApiAuth()],
        requestContentType: "application/json",
	] + apiParams
	log.debug "GET: $apiParams"
	try {
		httpGet(apiParams) { response -> 
        	callback(response)
        }
	}	catch (e)	{
		log.debug "API Error: $e"
	}
}

// HTTP PUT call
private apiPut(apiPath, apiParams = [], callback = {}) {	
	// set up parameters
	apiParams = [ 
		uri: getApiURL(),
		path: apiPath,
        headers: ["Authorization": getApiAuth()],
        requestContentType: "application/json",
	] + apiParams
	
	try {
		httpPut(apiParams) { response -> 
        	callback(response)
        }
	}	catch (e)	{
		log.debug "API Error: $e"
	}
}

private getApiURL() { 
	return "https://econet-api.rheemcert.com"
}
    
private getApiAuth() {
	return "Bearer " + state.session?.accessToken
}