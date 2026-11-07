import sys, time
sys.path.insert(0,'SCRATCH')
from kb import PH, tap_text, shot, sh
from ocr import strip
ph=sys.argv[1]; dev,_=PH[ph]
def clear_text(): sh(dev,'input tap 540 700; sleep 2.5; input keycombination 113 29; input keyevent 67'); time.sleep(0.6)
# 1. email typed in the Email field, then focus moves to Text
sh(dev,'input tap 500 400; sleep 2.5; input keycombination 113 29; input keyevent 67'); time.sleep(0.5)
tap_text(ph,'kbtest'); sh(dev,"input text '.'"); tap_text(ph,'mantra'); sh(dev,"input text '@'"); tap_text(ph,'example'); sh(dev,"input text '.'"); tap_text(ph,'com')
time.sleep(1); clear_text()
tap_text(ph,'kbt'); time.sleep(2.6); print('kbt ->', strip(ph, shot(ph,f'pe_{ph}_1')))
# 2. a phone number typed in a sentence
clear_text(); tap_text(ph,'call '); sh(dev,"input text '+385915550101'"); tap_text(ph,' ok'); time.sleep(1)
clear_text(); sh(dev,"input text '09155'"); time.sleep(2.6); print('09155 ->', strip(ph, shot(ph,f'pe_{ph}_2')))
clear_text(); tap_text(ph,'my number is '); time.sleep(2.6); print('my number is ->', strip(ph, shot(ph,f'pe_{ph}_3')))
