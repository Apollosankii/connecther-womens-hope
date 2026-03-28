from flask import Flask, url_for, redirect, render_template,request,session,flash
import requests
from werkzeug.utils import secure_filename
import os
import uuid
import supabase_data

app = Flask(__name__)
app.secret_key = b'_5#y2L"F4Q8z\n\xec]/'
# auth_header = ''
# app.config['Token'] = ''


def _portal_user():
    return {
        'name': 'Thurstan',
        'id': '32351791',
        'gender': 'M',
        'phone': '0711950607',
        'email': None,
        'pin': None,
        'postal': '70029',
        'physicaladd': 'Kanairo',
    }


def _to_bool(v, default=False):
    if v is None:
        return default
    return str(v).strip().lower() in ('1', 'true', 'yes', 'on')

@app.route('/',methods=['GET'])
def home():
    # session['ip']=request.remote_addr
    return render_template('index2.html')

@app.route('/quit',methods=['GET'])
def quit():
    # session['ip']=request.remote_addr
    return render_template('quitBase.html')

@app.route('/delete/account',methods=['GET'])
def delete_account():
    if "token" not in session:
        return redirect(url_for("quit"))
    
    return render_template('quit.html')
    # session['ip']=request.remote_addr
    
@app.route('/delete/',methods=['GET','POST'])
def effect_deletion():
    if "token" not in session:
        return redirect(url_for("quit"))
    # session['ip']=request.remote_addr
    payload = {'phone':request.form['phone']}
    print(payload)

    return redirect('https://womanshope.org')


@app.route('/privacy_statement',methods=['GET'])
def privacy():
    # session['ip']=request.remote_addr
    return render_template('PrivacyPolicy.htm')


@app.route('/sign_in',methods=['POST','GET'])
def sign_in():
        
    if request.method == 'POST':
        payload = {'email':request.form['identifier'],'password':request.form['password']}
        # response = requests.post('http://127.0.0.1:8000/admin/login',json=payload)
        response = requests.post('http://api:8000/admin/login',json=payload)

        
        if response.json()['info']=='success':
            # global auth_header
            session['token'] = {"Authorization": "Bearer {}".format(response.json()['token'])}

            # auth_header = {"Authorization": "Bearer {}".format(response.json()['token'])}
            # app.config["Token"]=auth_header
            # print(app.config["Token"])
            
            return redirect(url_for('dash'))

        else:
            return render_template('login.html')

            
@app.route('/quit/sign_in',methods=['POST','GET'])
def quit_sign_in():
        
    if request.method == 'POST':
        payload = {'phone':request.form['phone'],'password':request.form['password']}
        # response = requests.post('http://127.0.0.1:8000/login',json=payload)
        response = requests.post('http://api:8000/login',json=payload)

        print(response.json())
        if response.json()['info']=='Login Successfull':
            # global auth_header
            session['token'] = {"Authorization": "Bearer {}".format(response.json()['token'])}

            # auth_header = {"Authorization": "Bearer {}".format(response.json()['token'])}
            # app.config["Token"]=auth_header
            # print(app.config["Token"])
            
            return redirect(url_for('delete_account'))

        else:
            return redirect(url_for('quit'))

######################################################################################################################
# @app.route('/sign_up',methods=['POST','GET'])
# def sign_up():
#     idno=request.form['idno']
#     email=request.form['email']
#     phone=request.form['phone']
#     fullname=request.form['fullname']
#     password=request.form['password']

#     obj=Register(idno,email,phone,password,fullname,session['ip'])
    
#     if obj.conn() == 'Successful':
#         return redirect(url_for('dash')) 
#     else:
#         return redirect(url_for('fofofo'))

@app.route('/404')
def fofofo():
    return render_template('404.html')

@app.route('/oops')
def oops():
    return render_template('oops.html')


@app.route('/registration')
def registration():
    return render_template('login.html')

# These Routes Manage a Succesfully Logged in User
@app.route('/dash')
def dash():
    user={"name": "Admin 1",
          "email":'test@wh.org',
          "phone":'0712335678',
          "postal":'Kenya 1',
          "physicaladd": 'Nairobi'
          
        }
    context={'first':'first'}
    return render_template('landing.html',active_d='active',name=user['name'],user=user,balance=123.50,context=context)
    # return render_template('landing.html',active_d='active',name=user['name'],user=user,dash=session["dash"],charts=charts,balance=123.50,bar=bar,pie=pie,context=context)
    # else: return redirect(url_for('registration'))


####################################################################################################################
@app.route('/profile')
def profile():
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    return render_template('profile.html', user=user)

@app.route('/app_users')
def app_users():
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    headers=session.get('token')
    # headers={"Authorization":auth_header}
    # app_users = (requests.get("http://127.0.0.1:8000/list/app_users",headers=headers)).json()
    app_users = (requests.get("http://api:8000/list/app_users",headers=headers)).json()

    return render_template('appusers.html',user=user,app_users=app_users)

@app.route('/update/user-data/<user_id>')
def edit_user(user_id):
    headers=session.get('token')
    # provider = (requests.get("http://127.0.0.1:8000/get/provider/{}".format(user_id),headers=headers)).json()
    provider = (requests.get("http://api:8000/get/provider/{}".format(user_id),headers=headers)).json()
    services =(requests.get("http://api:8000/services")).json()['services']
    # app_users = (requests.get("http://api:8000/list/app_users",headers=headers)).json()
    print(provider)
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    
    return render_template('providerzoom.html',user=user,provider=provider,services=services)

@app.route('/flush/provider/', methods=['GET','POST'])
def flush_provider():
    headers=session.get('token')
    subs = request.form.getlist('subscriptions')
    user_kyc ={
            'first_name':request.form['first_name'],
            'last_name': request.form['last_name'],
            'phone': request.form['phone'],
            'county': request.form['county'],
            'area_name': request.form['area_name'],
            'nat_id': request.form['nat_id'],
            'emm_cont_1':request.form['emm_cont_1'],
            'emm_cont_2':request.form['emm_cont_2'],
            'user_id':request.form['user_id'],
            'title':request.form['title'],
            'subs':subs
            
        }
    print(user_kyc)
    # update = (requests.post("http://127.0.0.1:8000/edit/provider",headers=headers,json=user_kyc)).json()
    update = (requests.post("http://api:8000/edit/provider",headers=headers,json=user_kyc)).json()
    print(update)
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    
    return redirect(url_for('service_providers'))
#################################################################
# Handling All Service Operations
#################################################################
# Get All Services
@app.route('/services')
def services():
    # services = requests.get("http://127.0.0.1:8000/services")
    services = requests.get("http://api:8000/services")
    # print(services.json())
    services=services.json()['services']
    # print(type(services))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    
    return render_template('services.html', user=user,services=services)

# Get a Single Service by it's ID
@app.route('/service/details/<service_id>')
def service(service_id):
    headers=session.get('token')
    # service = requests.get("http://127.0.0.1:8000/service/{}".format(service_id),headers=headers)
    service = requests.get("http://api:8000/service/{}".format(service_id),headers=headers)
    # print(service_id)
    # print(service.json())
    service=service.json()["service"]
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}

    return render_template('service_Zoom.html',user=user,service=service)
    return redirect(url_for('profile'))
    # except Exception as e:
    # return redirect(url_for('dash'))
    # print(type(services))

# Function to Add a Service
@app.route('/add/service/', methods=['GET','POST'])
def add_service():
    # print("Service ID: {}".format(service_id))
    pic_dir = os.getcwd() + '/service_images'
    name=request.form['name']
    min_price=request.form['min_price']
    # serivice_id=request.form['id']
    description=request.form['description']
    category=request.form['category']
    service_pic=request.files['service_pic']
    # api_url="http://127.0.0.1:8000/add/service"
    api_url="http://api:8000/add/service"
    # Add Picture Upload
    if service_pic.filename=='':
    #    Check whether service Image has been uploaded
    # Condition NOt Uploaded
        payload ={'description':description,'min_price':min_price,'name':name}
        
        headers=session.get('token')
        response=requests.post(api_url,json=payload,headers=headers)
        # print(min_price)
        # print(description)
        print(response.json())
        return redirect(url_for('services'))
    else:
        filename=secure_filename(service_pic.filename)
        service_pic.save(os.path.join(pic_dir,filename))

        payload ={'description':description,'min_price':min_price,'name':name,'service_pic':filename}
        
        headers=session.get('token')
        response=requests.post(api_url,json=payload,headers=headers)
        # print(min_price)
        # print(description)
        print(response.json())
        return redirect(url_for('services'))


@app.route('/form/service/', methods=['GET','POST'])
def add_service_page():
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    return render_template('add_Service.html',user=user)

# Function to collect Service Update Form Data
@app.route('/service/update/<service_id>',methods=['GET','POST'])
def update_service(service_id):
    # print("Service ID: {}".format(service_id))
    # global auth_header
    headers=session.get('token')
    min_price=request.form['min_price']
    # serivice_id=request.form['id']
    description=request.form['description']
    category=request.form['category']
    payload ={'description':description,'min_price':min_price}
    # physical=request.form['physical']
    # api_url="http://127.0.0.1:8000/service/{}/update/".format(service_id)
    api_url="http://api:8000/service/{}/update/".format(service_id)
    response=requests.post(api_url,json=payload,headers=headers)
    # print(min_price)
    # print(description)
    print(response.json())
    return redirect(url_for('services'))

#########################################################################################
##### Handling Provider Registration ####################################################
@app.route('/provider', methods=['GET'])
def provider_page():
    # Get Services to pass service Ids for service selection in registration form

    # services = requests.get("http://127.0.0.1:8000/services")
    services = requests.get("http://api:8000/services")
    # documents= requests.get("http://127.0.0.1:8000/documents")
    documents= requests.get("http://api:8000/documents")
    documents = documents.json()
    services=services.json()['services']
    print(type(services))

    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    return render_template('registration.html',user=user,services=services,documents=documents)

@app.route('/add/provider', methods=['GET','POST'])
def add_provider():
    # DOC_LOC='/home/guyfawkes/projects/backend2/woman-global/Docs'
    # PROF_LOC ='/home/guyfawkes/projects/backend2/woman-global/profs'
    DOC_LOC= os.getcwd() + '/Docs'
    PROF_LOC = os.getcwd() + '/profs'
    if request.method == 'POST':
        # print("Service ID: {}".format(service_id))
        user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                    'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
        user_kyc ={
            'first_name':request.form['first_name'],
            'last_name': request.form['last_name'],
            'phone': request.form['phone'],
            'email': request.form['email'],
            'title': request.form['title'],
            'gender': request.form['gender'],
            'birth_date': request.form['dob'],
            'country': request.form['country'],
            'county': request.form['county'],
            'area_name': request.form['mtaa'],
            'nat_id': request.form['nat_id'],
            'emm_cont_1':request.form['emm_cont_1'],
            'emm_cont_2':request.form['emm_cont_2'],
            
        }
        user_loc ={
            'latitude': request.form['lat'],
            'longitude': request.form['long'],
        }
        # Check if Lat Long have been posted
        for key,value in user_loc.items():
            if value=='':
                user_loc = {}
                break

        user_docs = request.files
        clean_docs =[]
        clean_doc_dicts=[]
        for key,value in user_docs.items():
            # print(key)
            if user_docs[key].filename !='':
                zing ={}
                zing["doc_type_id"]=key
                zing["file"]=user_docs[key]
                clean_docs.append(zing)
            else:
                continue
        # Isolate profile Pic
        prof_pic = None
        counter = 0
        prof_loc = None
        while counter < len(clean_docs):
            
            if clean_docs[counter]['doc_type_id']=='profpic':
                prof_pic = clean_docs[counter]
                prof_loc = counter
                print('count: {}'.format(counter))
                break
            else:
                counter+=1
                print('count: {}'.format(counter))
                continue
        
        # Remove prof pics from documents
        # Check whether prof pic actually present
        if prof_loc !=None:
            clean_docs.pop(prof_loc)
            # Save the profpic
            ext=prof_pic['file'].filename.rsplit('.', 1)[1].lower()
            prof_pic['file'].filename=secure_filename(f"{uuid.uuid4()}.{ext}")
            prof_pic['file'].save(os.path.join(PROF_LOC,prof_pic['file'].filename))
            prof_pic['name']=prof_pic['file'].filename
            del prof_pic['file']
        else:
            pass

        #Check if Clean_docs is empty
        if not clean_docs:
            pass
        else:
            for doc in clean_docs:
                # Extract Extension
                ext=doc['file'].filename.rsplit('.', 1)[1].lower()
                doc['file'].filename=secure_filename(f"{uuid.uuid4()}.{ext}")
                doc['file'].save(os.path.join(DOC_LOC,doc['file'].filename))

                # print(f'File {doc['name'].filename} saved Successfully')
                #Embed Filename to data to be transmitted to API (Initiate User Documents)
                doc['name']=doc['file'].filename
                # Run clean docs ....
        # Drop the file object in document dictionary        
        for doc in clean_docs:
            del doc['file']


        # save_docs = {'docs':clean_docs}
        subs = request.form.getlist('subscriptions')
        # interesting way to merge dictionaries
        if prof_pic != None:
            user_kyc['prof_pic']=prof_pic['name']
        else:
            pass
        payload = {**user_kyc,**{'documents':clean_docs},**{'location':user_loc},**{'subs':subs}}
        print("PAY LOAD")
        print(payload)
        headers=session.get('token')
        # user_created = requests.post("http://127.0.0.1:8000/add/provider",json=payload,headers=headers)
        user_created = requests.post("http://api:8000/add/provider",json=payload,headers=headers)
        print("User Created: {}".format(user_created.json()))
        print(user_kyc)
        print(user_loc)
        # print(user_docs['nat_id'])
        print(clean_docs)
        print(subs)
        flash('User Created Successfully')

        return redirect(url_for('provider_page'))

@app.route('/providers', methods=['GET','POST'])    
def service_providers():
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                    'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    headers=session.get('token')
    # providers = (requests.get("http://127.0.0.1:8000/list/providers",headers=headers)).json()
    providers = (requests.get("http://api:8000/list/providers",headers=headers)).json()

    return render_template('providers.html',user=user,providers=providers)

###########################################################################################
########################### Approvals #####################################################
@app.route('/provider/approvals/', methods=['GET','POST'])
def get_approvals():
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    headers=session.get('token')
    # approvals=(requests.get('http://127.0.0.1:8000/providers/approvals',headers=headers)).json()
    approvals=(requests.get('http://api:8000/providers/approvals',headers=headers)).json()
    print(approvals)
    return render_template('approvals.html',user=user,approvals=approvals)

@app.route('/provider/approve/<user_id>', methods=['GET','POST'])
def approve_page(user_id):
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    print(user_id)
    # Fetch User to Approve
    headers=session.get('token')
    # provider=(requests.get('http://127.0.0.1:8000/provider/{}'.format(user_id),headers=headers)).json()
    provider=(requests.get('http://api:8000/provider/{}'.format(user_id),headers=headers)).json()
    print(provider)
    pending_app = supabase_data.get_pending_application_by_internal_id(session, user_id)
    provider_documents = supabase_data.list_user_verification_documents(session, user_id)
    return render_template(
        'Approve.html',
        user=user,
        provider=provider,
        pending_app=pending_app,
        provider_documents=provider_documents,
    )


@app.route('/provider/approve', methods=['POST'])
def approve():
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    prov_user_name=request.form["user_name"]
    # print(user_id)
    # Fetch User to Approve
    payload={'user_id':prov_user_name}
    headers=session.get('token')
    # response=requests.post('http://127.0.0.1:8000/provider/approve',json=payload,headers=headers).json()
    response=requests.post('http://api:8000/provider/approve',json=payload,headers=headers).json()
    print(response)

    return redirect(url_for('service_providers'))
#######################################################################################
############### Transactions ##########################################################
@app.route('/jobs/pending/', methods=['GET','POST'])
def pending_jobs():
    # global auth_header
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    headers=session.get('token')
    # jobs=(requests.get('http://127.0.0.1:8000/all_jobs/pending',headers=headers)).json()
    jobs=(requests.get('http://api:8000/all_jobs/pending',headers=headers)).json()
    # print(approvals)
    return render_template('ongoingjobs.html',user=user,jobs=jobs)

@app.route('/jobs/complete/', methods=['GET','POST'])
def complete_jobs():
    # print("Service ID: {}".format(service_id))
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    # print("Hapa Kuna Kizungumkuti")
    # print(app.config["Token"])
    headers=session.get('token')
    # jobs=(requests.get('http://127.0.0.1:8000/all_jobs/complete',headers=headers)).json()
    jobs=(requests.get('http://api:8000/all_jobs/complete',headers=headers)).json()
    print(jobs)
    return render_template('completejobs.html',user=user,jobs=jobs)

@app.route('/search', methods=['GET','POST'])
def search():
    headers=session.get('token')

    # api_url="http://127.0.0.1:8000/search"
    api_url="http://api:8000/search"
    query = request.args.get("query")
    payload ={
        'args':query
    }
    user={'name':'Thurstan',id:'32351791','gender':'M','phone':'0711950607',
                  'email':None,'pin':None,'postal':'70029','physicaladd':'Kanairo'}
    print(query)
    if query:
        response=requests.post(api_url,json=payload,headers=headers).json()
        return render_template('providersearch.html',providers=response)
    else:
        # providers = (requests.get("http://127.0.0.1:8000/list/providers",headers=headers)).json()
        providers = (requests.get("http://api:8000/list/providers",headers=headers)).json()
        return render_template('providersearch.html',providers=providers)


@app.route('/subscriptions/plans', methods=['GET'])
def subscription_plans():
    plans = supabase_data.list_subscription_plans(session, active_only=False)
    return render_template('subscription_plans.html', user=_portal_user(), plans=plans)


@app.route('/subscriptions/plans/add', methods=['GET', 'POST'])
def subscription_plan_add():
    if request.method == 'POST':
        payload = {
            'name': request.form.get('name', '').strip(),
            'description': request.form.get('description', '').strip(),
            'price': request.form.get('price', '0').strip() or '0',
            'currency': request.form.get('currency', 'KES').strip() or 'KES',
            'duration_type': request.form.get('duration_type', 'month').strip() or 'month',
            'duration_value': request.form.get('duration_value', '1').strip() or '1',
            'features': [x.strip() for x in (request.form.get('features', '') or '').splitlines() if x.strip()],
            'is_active': _to_bool(request.form.get('is_active'), True),
            'is_popular': _to_bool(request.form.get('is_popular'), False),
            'sort_order': request.form.get('sort_order', '0').strip() or '0',
            'connects_limit_enabled': _to_bool(request.form.get('connects_limit_enabled'), False),
            'connects_per_period': request.form.get('connects_per_period'),
            'connects_period_rule': request.form.get('connects_period_rule', 'subscription_term'),
        }
        ok = supabase_data.add_subscription_plan(session, payload)
        flash('Plan created.' if ok else 'Failed to create plan.')
        return redirect(url_for('subscription_plans'))
    return render_template('subscription_plan_form.html', user=_portal_user(), is_edit=False, plan=None)


@app.route('/subscriptions/plans/<int:plan_id>/edit', methods=['GET', 'POST'])
def subscription_plan_edit(plan_id):
    plan = supabase_data.get_subscription_plan(session, plan_id)
    if not plan:
        flash('Plan not found.')
        return redirect(url_for('subscription_plans'))
    if request.method == 'POST':
        action = request.form.get('_action', '')
        if action == 'delete':
            ok = supabase_data.delete_subscription_plan(session, plan_id)
            flash('Plan deleted.' if ok else 'Could not delete plan.')
            return redirect(url_for('subscription_plans'))
        if action == 'toggle_active':
            ok = supabase_data.update_subscription_plan(session, plan_id, {'is_active': not bool(plan.get('is_active'))})
            flash('Plan state updated.' if ok else 'Could not update plan state.')
            return redirect(url_for('subscription_plan_edit', plan_id=plan_id))
        payload = {
            'name': request.form.get('name', '').strip(),
            'description': request.form.get('description', '').strip() or None,
            'price': float(request.form.get('price', '0') or 0),
            'currency': request.form.get('currency', 'KES').strip() or 'KES',
            'duration_type': request.form.get('duration_type', 'month'),
            'duration_value': int(request.form.get('duration_value', '1') or 1),
            'features': [x.strip() for x in (request.form.get('features', '') or '').splitlines() if x.strip()],
            'is_active': _to_bool(request.form.get('is_active'), False),
            'is_popular': _to_bool(request.form.get('is_popular'), False),
            'sort_order': int(request.form.get('sort_order', '0') or 0),
            'connects_limit_enabled': _to_bool(request.form.get('connects_limit_enabled'), False),
            'connects_per_period': request.form.get('connects_per_period') if request.form.get('connects_per_period', '').strip() != '' else None,
            'connects_period_rule': request.form.get('connects_period_rule', 'subscription_term'),
        }
        ok = supabase_data.update_subscription_plan(session, plan_id, payload)
        flash('Plan updated.' if ok else 'Failed to update plan.')
        return redirect(url_for('subscription_plan_edit', plan_id=plan_id))
    return render_template('subscription_plan_form.html', user=_portal_user(), is_edit=True, plan=plan)


@app.route('/subscriptions/free-tier', methods=['GET', 'POST'])
def subscription_free_tier():
    if request.method == 'POST':
        value = int(request.form.get('free_tier_connects', '5') or 5)
        ok = supabase_data.update_platform_settings(session, value)
        flash('Free tier updated.' if ok else 'Failed to update free tier.')
        return redirect(url_for('subscription_free_tier'))
    settings = supabase_data.get_platform_settings(session)
    return render_template('subscription_free_tier.html', user=_portal_user(), settings=settings)


@app.route('/subscriptions/users', methods=['GET'])
def subscription_user_subscriptions():
    status_filter = (request.args.get('status') or '').strip() or None
    plan_filter = (request.args.get('plan_id') or '').strip() or None
    plans = supabase_data.list_subscription_plans(session, active_only=False)
    subscriptions = supabase_data.list_user_plan_subscriptions(session, status_filter=status_filter, plan_filter=plan_filter)
    return render_template(
        'subscription_user_subscriptions.html',
        user=_portal_user(),
        subscriptions=subscriptions,
        plans=plans,
        status_filter=status_filter or '',
        plan_filter=plan_filter or '',
    )


@app.route('/subscriptions/users/grant', methods=['GET', 'POST'])
def subscription_grant():
    plans = supabase_data.list_subscription_plans(session, active_only=True)
    app_users = supabase_data.list_users_for_plan_grant(session)
    if request.method == 'POST':
        user_id = int(request.form.get('user_id'))
        plan_id = int(request.form.get('plan_id'))
        started_at = request.form.get('started_at')
        expires_at = request.form.get('expires_at')
        payment_reference = request.form.get('payment_reference')
        notes = request.form.get('notes')
        override = request.form.get('connects_granted_override')
        override_value = int(override) if override and override.strip() != '' else None
        ok, err = supabase_data.grant_user_plan_subscription(
            session,
            user_id=user_id,
            plan_id=plan_id,
            started_at=started_at,
            expires_at=expires_at,
            payment_reference=payment_reference,
            notes=notes,
            connects_granted_override=override_value,
        )
        flash('Subscription granted.' if ok else f'Failed to grant subscription ({err or "unknown_error"}).')
        return redirect(url_for('subscription_user_subscriptions'))
    return render_template('subscription_grant.html', user=_portal_user(), plans=plans, app_users=app_users)


@app.route('/subscriptions/users/<int:sub_id>', methods=['GET', 'POST'])
def subscription_user_detail(sub_id):
    if request.method == 'POST':
        action = request.form.get('_action', '')
        if action == 'cancel':
            ok = supabase_data.cancel_user_plan_subscription(session, sub_id)
            flash('Subscription cancelled.' if ok else 'Failed to cancel subscription.')
        elif action == 'extend':
            ok = supabase_data.extend_user_plan_subscription(session, sub_id, request.form.get('new_expires_at'))
            flash('Subscription extended.' if ok else 'Failed to extend subscription.')
        elif action == 'update':
            payload = {
                'status': request.form.get('status'),
                'expires_at': request.form.get('expires_at') or None,
                'payment_reference': request.form.get('payment_reference') or None,
                'notes': request.form.get('notes') or None,
            }
            ok = supabase_data.update_user_plan_subscription(session, sub_id, payload)
            flash('Subscription updated.' if ok else 'Failed to update subscription.')
        elif action == 'update_connects':
            mode = request.form.get('connect_balance_mode', 'limited')
            payload = {
                'connects_granted': None if mode == 'unlimited' else int(request.form.get('connects_granted', '0') or 0),
                'connects_used': int(request.form.get('connects_used', '0') or 0),
                'connects_period_started_at': request.form.get('connects_period_started_at') or None,
            }
            ok = supabase_data.update_user_plan_subscription(session, sub_id, payload)
            flash('Connect balance updated.' if ok else 'Failed to update connect balance.')
        return redirect(url_for('subscription_user_detail', sub_id=sub_id))
    sub = supabase_data.get_user_plan_subscription(session, sub_id)
    if not sub:
        flash('Subscription not found.')
        return redirect(url_for('subscription_user_subscriptions'))
    return render_template('subscription_user_detail.html', user=_portal_user(), sub=sub)
 

if __name__ == '__main__':
   app.run()  