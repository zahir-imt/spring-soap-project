"""Run only against a disposable QuoteCraft database. Creates fictional test data."""
import urllib.request, urllib.parse, urllib.error, http.cookiejar, re, secrets, datetime, time
base='http://127.0.0.1:8082'
jar=http.cookiejar.CookieJar()
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
def get(path):
 with client.open(base+path) as r:return r.geturl(),r.read().decode()
def token(page):
 return re.search(r'name="_csrf"[^>]*value="([^"]+)"',page).group(1)
def post(path,fields,page):
 fields=dict(fields);fields['_csrf']=token(page)
 with client.open(base+path,urllib.parse.urlencode(fields,doseq=True).encode()) as r:return r.geturl(),r.read().decode()
for attempt in range(30):
 try:get('/');break
 except urllib.error.URLError:time.sleep(1)
else:raise RuntimeError('Test server did not start')
email='smoke-'+secrets.token_hex(5)+'@example.test';password=secrets.token_urlsafe(24)
_,page=get('/signup')
_,page=post('/signup',{'name':'Fictional QA Cleaning','email':email,'password':password},page)
assert 'Workspace created' in page
url,page=post('/login',{'email':email,'password':password},page)
assert url.endswith('/dashboard') and 'Good work' in page
_,page=get('/quotes/new')
url,page=post('/quotes',{'customer':'Fictional customer','email':'customer@example.test','address':'123 Example Street','title':'Smoke-test clean','expires':(datetime.date.today()+datetime.timedelta(days=14)).isoformat(),'notes':'Fictional automated QA only','description':['Cleaning','Windows'],'quantity':['2','1'],'price':['100','50'],'discount':'10','tax':'13'},page)
path=urllib.parse.urlparse(url).path
assert '271.20' in page
url,page=post(path+'/share',{},page)
review=re.search(r'id="review-link" href="([^"]+)"',page).group(1)
with client.open(base+path+'/pdf') as r:assert r.read().startswith(b'%PDF-')
_,page=get(review)
url,page=post(review,{'name':'Fictional customer','action':'accept','feedback':'QA approval only'},page)
assert 'ACCEPTED' in page
_,page=get(path)
_,page=post(path+'/schedule',{'date':(datetime.date.today()+datetime.timedelta(days=1)).isoformat()},page)
_,page=post(path+'/advance',{},page)
assert 'IN PROGRESS' in page
_,page=post(path+'/advance',{},page)
assert 'COMPLETED' in page
_,page=get('/jobs');assert 'Smoke-test clean' in page
_,page=post('/logout',{},page)
url,page=get('/dashboard');assert url.endswith('/login')
print('PASS: real HTTP signup, login, quote creation, share, PDF, approval, scheduling, completion, job board and logout')
