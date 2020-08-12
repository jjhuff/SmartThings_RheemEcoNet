SmartThings Rheem EcoNet
===============

NOTE: this doesn't work anymore due to changes in Rheem's API.

This is based on https://github.com/copy-ninja/SmartThings_RheemEcoNet with updates for the new API

SmartThings installation instructions:
--------------------------------------
1) Log in to your the <a href="https://graph.api.smartthings.com/ide/">SmartThings IDE</a>. If you don't have a login yet, create one.

2) Load contents of [Smart App](smartapps/jjhuff/rheem-econet-connect.src/rheem-econet-connect.groovy) in SmartApps section. From IDE, navigate to <a href="https://graph.api.smartthings.com/ide/app/create#from-code">My SmartApps > + New SmartApp > From Code</a>. Click Save. Click Publish > "For Me"

3) Load contents of [Device Handler](devicetypes/jjhuff/rheem-econet-water-heater.src/rheem-econet-water-heater.groovy) in Device Handlers section. From IDE, navigate to <a href="https://graph.api.smartthings.com/ide/device/create#from-code">My Device Handler > + New SmartDevice > From Code</a>.  Click Save. Click Publish "For Me"

4) In your mobile app, tap the "+", go to "My Apps", furnish your log in details and pick your gateway brand, and a list of devices will be available for you to pick

Donations
---------
If you like this project, please consider donating to one of these:
* [EFF](https://www.eff.org/)
* [Let's Encrypt](https://letsencrypt.org/) 
