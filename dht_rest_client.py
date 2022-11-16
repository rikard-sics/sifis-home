import requests

api_url = "http://localhost:3000/"

"""
#insert Socket
topic_name = "SIFIS::Sockets"
topic_uuid = "FirstSocket"
socket = {"socket_uuid": "FirstSocket", "description": "First DHT Socket", "connected": True}
response = requests.post(api_url + "topic_name/" + topic_name + "/topic_uuid/" + topic_uuid, json=socket)
print(response.json())


#insert Light
topic_name = "SIFIS::Lights"
topic_uuid="FirstLight"
light = {"light_uuid": "FirstLight", "description": "First DHT Light", "connected": True}
response = requests.post(api_url + "topic_name/" + topic_name + "/topic_uuid/" + topic_uuid, json=light)
print(response.json())

# get_all
response = requests.get(api_url + "get_all");
print(response.json())

#get_topic_name
topic_name = "SIFIS::Lights"
response = requests.get(api_url + "topic_name/" + topic_name)
print(response.json())

#get_topic_uuid
response = requests.get(api_url + "topic_name/" + topic_name + "/topic_uuid/" + topic_uuid)
print(response.json())

#publish volatile message
volatile_message = {"message": "hi" }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())

#publish volatile message (Group OSCORE Client 1)
volatile_message = {"message": "on", "topic": "command_dev1" }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())

#publish volatile message (Group OSCORE Client 2)
volatile_message = {"message": "on", "topic": "command_dev2" }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())

#publish volatile message (EDHOC Client)
volatile_message = {"message": "on", "topic": "command_ed" }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())
"""

# To install script requirements run:
# pip install -r python_requirements.txt
# Codebase for DHT and original example script:
# https://github.com/domo-iot/sifis-dht-test

print("1. Group OSCORE Client #1");
print("2. Group OSCORE Client #2");
print("3. EDHOC Client");
valTarget = input("Enter device to send to: ")
valPayload = input("Enter payload to send: ")

if valTarget in ['1']:
    topic = "command_dev1"
elif valTarget in ['2']:
    topic = "command_dev2"
elif valTarget in ['3']:
    topic = "command_ed"
else:
    print("Invalid command")
    quit();

#publish volatile message
volatile_message = {"message": valPayload, "topic": topic }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())

