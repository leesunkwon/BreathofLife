// -------------------
// 설정 및 초기화
// -------------------

// 본인의 Firebase 프로젝트 설정 값으로 교체하세요.
// API 키는 외부에 노출되면 안 되는 민감한 정보이므로 제거했습니다.
const firebaseConfig = {
    apiKey: "YOUR_KEY",
    authDomain: "YOUR_KEY",
    projectId: "YOUR_KEY",
    storageBucket: "YOUR_KEY",
    messagingSenderId: "YOUR_KEY",
    appId: "YOUR_KEY"
};

// 가나다순으로 정렬된 전체 진료과 목록 (선택 옵션으로 사용)
const ALL_DEPARTMENTS = [
    "가정의학과", "내과", "마취통증의학과", "병리과", "비뇨의학과", "산부인과",
    "성형외과", "소아청소년과", "신경외과", "안과", "영상의학과", "응급의학과",
    "이비인후과", "재활의학과", "정신건강의학과", "정형외과", "직업환경의학과",
    "진단검사의학과", "피부과", "핵의학과", "흉부외과"
];

// Firebase 서비스 초기화
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth(); // Firebase 인증 서비스
const db = firebase.firestore(); // Firebase Firestore 데이터베이스 서비스


// -------------------
// HTML 요소 가져오기
// -------------------
// 스크립트에서 제어할 HTML 요소들을 변수에 할당합니다.
const authContainer = document.getElementById('auth-container');
const managementContainer = document.getElementById('management-container');
const loginContainer = document.getElementById('login-container');
const signupContainer = document.getElementById('signup-container');
const showSignupLink = document.getElementById('show-signup-link');
const showLoginLink = document.getElementById('show-login-link');
const loginForm = document.getElementById('login-form');
const signupForm = document.getElementById('signup-form');
const logoutButton = document.getElementById('logout-button');
const managementTitle = document.getElementById('management-title');
const updateBedsButton = document.getElementById('update-beds-button');
const updateTotalBeds = document.getElementById('total-beds');
const updateAvailableBeds = document.getElementById('available-beds');
const updateMessage = document.getElementById('update-message');
const departmentSelect = document.getElementById('department-select');
const addDepartmentButton = document.getElementById('add-department-button');
const departmentTagsContainer = document.getElementById('department-tags-container');
const callsContainer = document.getElementById('calls-container');
const noCallsMessage = document.getElementById('no-calls-message');
const acceptedCallsContainer = document.getElementById('accepted-calls-container');
const noAcceptedCallsMessage = document.getElementById('no-accepted-calls-message');


// -------------------
// 전역 변수
// -------------------
// 스크립트 전반에서 사용될 변수들을 선언합니다.
let currentHospitalId = null; // 현재 로그인한 병원의 문서 ID
let currentHospitalName = null; // 현재 로그인한 병원의 이름
let pendingCallsListener = null; // 대기중인 요청 실시간 리스너
let acceptedCallsListener = null; // 수락한 요청 실시간 리스너


// -------------------
// 핵심 기능 함수
// -------------------

/**
 * 진료과 선택 드롭다운 목록을 ALL_DEPARTMENTS 배열을 기반으로 채웁니다.
 */
function populateDepartmentSelect() {
    departmentSelect.innerHTML = '<option value="">-- 진료과 선택 --</option>';
    ALL_DEPARTMENTS.forEach(dept => {
        const option = document.createElement('option');
        option.value = dept;
        option.textContent = dept;
        departmentSelect.appendChild(option);
    });
}

/**
 * 현재 병원의 진료 가능 과 목록을 태그 형태로 화면에 표시합니다.
 * @param {string[]} departments - 표시할 진료과 이름 배열
 */
function renderDepartmentTags(departments = []) {
    departmentTagsContainer.innerHTML = '';
    departments.forEach(dept => {
        const tag = document.createElement('div');
        tag.className = 'department-tag';
        tag.textContent = dept;
        const removeBtn = document.createElement('span');
        removeBtn.className = 'remove-tag';
        removeBtn.textContent = 'x';
        removeBtn.onclick = () => removeDepartment(dept); // 'x' 버튼 클릭 시 삭제 함수 호출
        tag.appendChild(removeBtn);
        departmentTagsContainer.appendChild(tag);
    });
}

/**
 * 선택한 진료과를 Firestore의 병원 정보에 추가합니다.
 */
function addDepartment() {
    const selectedDept = departmentSelect.value;
    if (!selectedDept || !currentHospitalId) return; // 유효성 검사

    const hospitalDocRef = db.collection('hospitals').doc(currentHospitalId);
    // arrayUnion을 사용해 중복 없이 배열에 요소를 추가합니다.
    hospitalDocRef.update({
        availableDepartments: firebase.firestore.FieldValue.arrayUnion(selectedDept)
    }).then(() => {
        // 성공 시 화면을 다시 렌더링합니다.
        db.collection('hospitals').doc(currentHospitalId).get().then(doc => renderDepartmentTags(doc.data().availableDepartments));
        departmentSelect.value = ""; // 드롭다운 초기화
    });
}

/**
 * 특정 진료과를 Firestore의 병원 정보에서 삭제합니다.
 * @param {string} deptName - 삭제할 진료과 이름
 */
function removeDepartment(deptName) {
    if (!deptName || !currentHospitalId) return; // 유효성 검사

    const hospitalDocRef = db.collection('hospitals').doc(currentHospitalId);
    // arrayRemove를 사용해 배열에서 특정 요소를 제거합니다.
    hospitalDocRef.update({
        availableDepartments: firebase.firestore.FieldValue.arrayRemove(deptName)
    }).then(() => {
        // 성공 시 화면을 다시 렌더링합니다.
        db.collection('hospitals').doc(currentHospitalId).get().then(doc => renderDepartmentTags(doc.data().availableDepartments));
    });
}

/**
 * 치료 완료 처리: 'emergency_calls' 컬렉션의 원본 요청은 삭제하고,
 * 해당 병원의 하위 컬렉션 'completed_cases'에 데이터를 복사하여 기록으로 남깁니다.
 * @param {string} callId - 완료 처리할 요청의 문서 ID
 * @param {object} callData - 완료 처리할 요청의 데이터
 */
function completeCase(callId, callData) {
    if (!currentHospitalId || !currentHospitalName) return;

    const batch = db.batch(); // 여러 작업을 원자적으로 처리하기 위해 batch 사용

    // 1. 병원 기록용 데이터 생성
    const newCaseRef = db.collection('hospitals').doc(currentHospitalId).collection('completed_cases').doc(callId);
    const caseRecord = {
        ...callData,
        status: 'completed', // 상태를 '완료'로 변경
        acceptedHospitalName: currentHospitalName,
        caseCompletedAt: firebase.firestore.FieldValue.serverTimestamp() // 완료 시각 기록
    };
    batch.set(newCaseRef, caseRecord); // 배치에 쓰기 작업 추가

    // 2. 원본 응급 요청 삭제
    const originalCallRef = db.collection('emergency_calls').doc(callId);
    batch.delete(originalCallRef); // 배치에 삭제 작업 추가

    // 3. 배치 작업 실행
    batch.commit().then(() => {
        alert("환자 치료를 완료하고 병원 기록으로 이전했습니다.\n(원본 요청은 삭제되었습니다)");
    }).catch(error => {
        console.error("치료 완료 처리 오류:", error);
        alert("치료 완료 처리 중 오류가 발생했습니다.");
    });
}

/**
 * 응급 요청을 수락합니다.
 * Firestore 트랜잭션을 사용하여 여러 병원이 동시에 수락하는 것을 방지합니다.
 * @param {string} callId - 수락할 요청의 문서 ID
 */
function acceptCall(callId) {
    if (!currentHospitalId || !currentHospitalName) return;
    const callDocRef = db.collection("emergency_calls").doc(callId);
    
    // 트랜잭션: 데이터의 일관성을 보장하기 위해 읽기와 쓰기를 한 묶음으로 처리
    db.runTransaction(transaction => {
        return transaction.get(callDocRef).then(callDoc => {
            if (!callDoc.exists) throw "요청 문서를 찾을 수 없습니다.";
            // 수락하기 전에 상태가 여전히 'pending'인지 확인
            if (callDoc.data().status !== 'pending') throw "이미 다른 병원에서 수락한 요청입니다.";
            
            // 상태를 'accepted'로 업데이트하고 수락한 병원 정보 기록
            transaction.update(callDocRef, {
                status: "accepted",
                acceptedHospitalId: currentHospitalId,
                acceptedHospitalName: currentHospitalName,
                acceptedAt: firebase.firestore.FieldValue.serverTimestamp()
            });
        });
    }).then(() => {
        alert("응급 요청을 수락했습니다.");
    }).catch(error => {
        alert(`오류: ${error}`);
        // 오류 발생 시 (주로 다른 병원이 먼저 수락한 경우) 화면에서 해당 카드 제거
        const cardToRemove = document.getElementById(`pending-call-${callId}`);
        if (cardToRemove) cardToRemove.remove();
    });
}

/**
 * 응급 요청을 거절합니다.
 * 거절한 병원 ID를 'rejectedBy' 배열에 추가하여 해당 병원에게 다시 보이지 않도록 합니다.
 * @param {string} callId - 거절할 요청의 문서 ID
 */
function rejectCall(callId) {
    db.collection("emergency_calls").doc(callId).update({
        rejectedBy: firebase.firestore.FieldValue.arrayUnion(currentHospitalId)
    });
}

/**
 * 대기 중인 응급 요청을 실시간으로 감지하고 화면에 표시합니다.
 * onSnapshot 리스너를 사용하여 DB 변경이 실시간으로 앱에 반영됩니다.
 */
function listenForPendingCalls() {
    // 기존 리스너가 있다면 중복 실행을 막기 위해 해제
    if (pendingCallsListener) pendingCallsListener();
    
    // 'targetedHospitalIds' 배열에 현재 병원 ID가 포함된 'pending' 상태의 요청만 조회
    const query = db.collection('emergency_calls')
        .where('status', '==', 'pending')
        .where('targetedHospitalIds', 'array-contains', currentHospitalId);

    pendingCallsListener = query.onSnapshot(snapshot => {
        // 변경된 문서만 처리하여 효율성 증대
        snapshot.docChanges().forEach(change => {
            const callData = change.doc.data();
            const callId = change.doc.id;
            const cardElement = document.getElementById(`pending-call-${callId}`);
            // 내가 거절했는지 확인
            const isRejected = callData.rejectedBy && callData.rejectedBy.includes(currentHospitalId);

            // [화면에서 제거할 조건]
            // 1. 내가 거절한 경우
            // 2. 문서가 삭제된 경우
            // 3. 문서 상태가 더 이상 'pending'이 아닌 경우 (예: 다른 병원이 수락)
            if (isRejected || change.type === 'removed' || (change.type === 'modified' && callData.status !== 'pending')) {
                if (cardElement) cardElement.remove();
                return;
            }
            // [화면에 추가할 조건]
            // 1. 새로운 문서가 추가되었고, 아직 화면에 없는 경우
            if (change.type === 'added' && !cardElement) {
                const card = createPendingCallCard(callId, callData);
                callsContainer.prepend(card); // 새 요청을 맨 위에 추가
            }
        });
        // 요청이 하나도 없으면 "없음" 메시지 표시
        noCallsMessage.style.display = callsContainer.querySelectorAll('.call-card').length === 0 ? 'block' : 'none';
    }, error => console.error("대기 요청 리스너 오류:", error));
}

/**
 * 현재 병원이 '수락한' 응급 요청을 실시간으로 감지하고 화면에 표시합니다.
 */
function listenForAcceptedCalls() {
    if (acceptedCallsListener) acceptedCallsListener();

    // 'acceptedHospitalId'가 현재 병원 ID와 일치하고, 상태가 'accepted'인 요청만 조회
    const query = db.collection('emergency_calls')
        .where('acceptedHospitalId', '==', currentHospitalId)
        .where('status', '==', 'accepted');
        
    acceptedCallsListener = query.onSnapshot(snapshot => {
        snapshot.docChanges().forEach(change => {
            const callData = change.doc.data();
            const callId = change.doc.id;
            const cardElement = document.getElementById(`accepted-call-${callId}`);

            // [화면에서 제거할 조건] (예: 치료 완료 후 원본 요청이 삭제된 경우)
            if (change.type === 'removed' || (change.type === 'modified' && change.doc.data().status !== 'accepted')) {
                if (cardElement) cardElement.remove();
                return;
            }
            // [화면에 추가할 조건]
            if (change.type === 'added' && !cardElement) {
                const card = createAcceptedCallCard(callId, callData);
                acceptedCallsContainer.prepend(card);
            }
        });
        // 수락한 요청이 없으면 "없음" 메시지 표시
        noAcceptedCallsMessage.style.display = acceptedCallsContainer.querySelectorAll('.accepted-call-card').length === 0 ? 'block' : 'none';
    }, error => console.error("수락 요청 리스너 오류:", error));
}

/**
 * 대기 중인 요청 카드의 HTML 요소를 생성합니다.
 * @param {string} id - 요청 문서 ID
 * @param {object} data - 요청 데이터
 * @returns {HTMLElement} - 생성된 카드 div 요소
 */
function createPendingCallCard(id, data) {
    const card = document.createElement('div');
    card.className = 'call-card';
    card.id = `pending-call-${id}`;
    const patient = data.patientInfo;
    const creationTime = data.createdAt ? data.createdAt.toDate().toLocaleTimeString('ko-KR') : '시간 없음';
    card.innerHTML = `
        <h3>환자: ${patient.name} (${patient.age}세, ${patient.gender})</h3>
        <p><strong>주요 증상:</strong> ${patient.symptom}</p>
        <p><strong>기타 정보:</strong> ${patient.otherInfo || '없음'}</p>
        <p><strong>요청 시각:</strong> ${creationTime}</p>
        <div class="call-actions">
            <button class="btn" data-action="accept" data-id="${id}">수락</button>
            <button class="btn btn-secondary" data-action="reject" data-id="${id}">거절</button>
        </div>
    `;
    return card;
}

/**
 * 수락한 요청 카드의 HTML 요소를 생성합니다.
 * @param {string} id - 요청 문서 ID
 * @param {object} data - 요청 데이터
 * @returns {HTMLElement} - 생성된 카드 div 요소
 */
function createAcceptedCallCard(id, data) {
    const card = document.createElement('div');
    card.className = 'accepted-call-card';
    card.id = `accepted-call-${id}`;
    const patient = data.patientInfo;
    const acceptedTime = data.acceptedAt ? data.acceptedAt.toDate().toLocaleTimeString('ko-KR') : '시간 없음';
    card.innerHTML = `
        <h3>환자: ${patient.name} (${patient.age}세, ${patient.gender})</h3>
        <p><strong>주요 증상:</strong> ${patient.symptom}</p>
        <p><strong>수락 시각:</strong> ${acceptedTime}</p>
        <div class="accepted-call-actions">
            <button class="btn" data-action="complete" data-id="${id}">치료 완료</button>
        </div>
    `;
    // '치료 완료' 버튼에 직접 이벤트 리스너 추가
    card.querySelector('button[data-action="complete"]').addEventListener('click', () => {
        if (confirm(`${patient.name} 환자의 치료를 완료하고 기록을 저장하시겠습니까?`)) {
            completeCase(id, data);
        }
    });
    return card;
}


// -------------------
// 이벤트 리스너 설정
// -------------------

// '회원가입' 링크 클릭 시 화면 전환
showSignupLink.addEventListener('click', (e) => { e.preventDefault(); loginContainer.style.display = 'none'; signupContainer.style.display = 'block'; });
// '로그인' 링크 클릭 시 화면 전환
showLoginLink.addEventListener('click', (e) => { e.preventDefault(); signupContainer.style.display = 'none'; loginContainer.style.display = 'block'; });

// 로그인 폼 제출 이벤트
loginForm.addEventListener('submit', (e) => {
    e.preventDefault(); // 폼 기본 제출 동작 방지
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;
    auth.signInWithEmailAndPassword(email, password).catch(error => {
        let msg = "로그인 중 오류가 발생했습니다.";
        if (error.code === 'auth/user-not-found' || error.code === 'auth/wrong-password') {
            msg = "이메일 또는 비밀번호가 올바르지 않습니다.";
        }
        alert(msg);
    });
});

// 회원가입 폼 제출 이벤트
signupForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const email = document.getElementById('signup-email').value;
    const password = document.getElementById('signup-password').value;
    const hospitalName = document.getElementById('hospital-name').value;
    const address = document.getElementById('hospital-address').value;
    const lat = document.getElementById('hospital-lat').value;
    const lon = document.getElementById('hospital-lon').value;

    const latitude = parseFloat(lat);
    const longitude = parseFloat(lon);

    if (isNaN(latitude) || isNaN(longitude)) {
        alert("유효한 위도와 경도를 숫자로 입력해주세요.");
        return;
    }

    // 1. Firebase Auth에 사용자 생성
    auth.createUserWithEmailAndPassword(email, password)
        .then(userCredential => {
            // 2. Firestore에 병원 정보와 사용자 정보 저장 (batch 사용)
            const batch = db.batch();
            const hospitalRef = db.collection('hospitals').doc(); // 새 병원 문서 참조 생성
            
            // 병원 정보 저장
            batch.set(hospitalRef, {
                hospitalName: hospitalName,
                address: { full: address },
                location: new firebase.firestore.GeoPoint(latitude, longitude),
                beds: { total: 20, available: 5, lastUpdated: firebase.firestore.FieldValue.serverTimestamp() },
                availableDepartments: []
            });
            
            // 사용자 정보 저장 (Auth UID와 병원 문서 ID 연결)
            const userRef = db.collection('users').doc(userCredential.user.uid);
            batch.set(userRef, { email, name: "병원 관리자", role: 'hospital', hospitalId: hospitalRef.id });
            
            return batch.commit(); // 배치 작업 실행
        })
        .then(() => alert('회원가입이 완료되었습니다. 자동으로 로그인됩니다.'))
        .catch(error => {
            let msg = `회원가입 중 오류가 발생했습니다: ${error.message}`;
            if (error.code === 'auth/email-already-in-use') msg = "이미 사용 중인 이메일입니다.";
            alert(msg);
        });
});

// 로그아웃 버튼 클릭 이벤트
logoutButton.addEventListener('click', () => auth.signOut());

// 병상 정보 업데이트 버튼 클릭 이벤트
updateBedsButton.addEventListener('click', () => {
    if (!currentHospitalId) return;
    const totalBeds = parseInt(updateTotalBeds.value, 10);
    const availableBeds = parseInt(updateAvailableBeds.value, 10);
    
    // 입력값 유효성 검사
    if (isNaN(totalBeds) || isNaN(availableBeds) || totalBeds < 0 || availableBeds < 0 || availableBeds > totalBeds) {
        alert("병상 수를 올바르게 입력해주세요.");
        return;
    }
    
    // Firestore 문서 업데이트
    db.collection('hospitals').doc(currentHospitalId).update({
        'beds.total': totalBeds, 
        'beds.available': availableBeds, 
        'beds.lastUpdated': firebase.firestore.FieldValue.serverTimestamp()
    }).then(() => {
        updateMessage.textContent = '업데이트 완료! (' + new Date().toLocaleTimeString() + ')';
        setTimeout(() => updateMessage.textContent = '', 3000); // 3초 후 메시지 숨김
    });
});

// 진료과 추가 버튼 클릭 이벤트
addDepartmentButton.addEventListener('click', addDepartment);

// 대기중인 요청 목록의 버튼 클릭 이벤트 (이벤트 위임)
// 부모 요소(callsContainer)에 이벤트 리스너를 하나만 추가하여 효율적으로 관리
callsContainer.addEventListener('click', (e) => {
    // 클릭된 요소가 버튼이고, 'data-action' 속성이 있을 때만 동작
    if (e.target.tagName !== 'BUTTON' || !e.target.dataset.action) return;
    
    const { action, id } = e.target.dataset; // data-* 속성값 가져오기
    if (action === 'accept') {
        if (confirm("이 응급 요청을 수락하시겠습니까?")) acceptCall(id);
    } else if (action === 'reject') {
        rejectCall(id);
    }
});


// ------------------------
// 인증 상태 변경 리스너 (메인 로직)
// ------------------------
// 사용자의 로그인/로그아웃 상태가 변경될 때마다 자동으로 실행됩니다.
auth.onAuthStateChanged(user => {
    if (user) {
        // --- 로그인 상태일 때 ---
        // 1. UI 변경
        authContainer.style.display = 'none';
        managementContainer.style.display = 'block';
        
        // 2. 사용자 정보와 연결된 병원 정보 불러오기
        db.collection('users').doc(user.uid).get().then(userDoc => {
            if (!userDoc.exists) { auth.signOut(); return Promise.reject("User doc not found."); }
            currentHospitalId = userDoc.data().hospitalId;
            return db.collection('hospitals').doc(currentHospitalId).get();
        }).then(hospitalDoc => {
            if (!hospitalDoc.exists) { auth.signOut(); return Promise.reject("Hospital doc not found."); }
            
            // 3. 불러온 데이터로 화면 채우기
            const hospitalData = hospitalDoc.data();
            currentHospitalName = hospitalData.hospitalName;
            managementTitle.textContent = `${hospitalData.hospitalName} 응급실 관리`;
            updateTotalBeds.value = hospitalData.beds.total;
            updateAvailableBeds.value = hospitalData.beds.available;
            renderDepartmentTags(hospitalData.availableDepartments);

            // 4. 실시간 리스너 시작
            listenForPendingCalls();
            listenForAcceptedCalls();
        }).catch(error => {
            console.error("데이터 로드 중 오류:", error);
            auth.signOut(); // 오류 발생 시 강제 로그아웃
        });
    } else {
        // --- 로그아웃 상태일 때 ---
        // 1. UI 변경
        authContainer.style.display = 'block';
        managementContainer.style.display = 'none';
        
        // 2. 폼 및 화면 초기화
        loginForm.reset();
        signupForm.reset();
        signupContainer.style.display = 'none';
        loginContainer.style.display = 'block';
        
        // 3. 전역 변수 초기화
        currentHospitalId = null;
        currentHospitalName = null;

        // 4. 활성화된 리스너 해제 (메모리 누수 방지)
        if (pendingCallsListener) pendingCallsListener();
        if (acceptedCallsListener) acceptedCallsListener();

        // 5. 요청 목록 비우기
        callsContainer.innerHTML = '<p id="no-calls-message">현재 대기 중인 응급 요청이 없습니다.</p>';
        acceptedCallsContainer.innerHTML = '<p id="no-accepted-calls-message">현재 치료 중인 환자가 없습니다.</p>';
    }
});

// --- 앱 시작 시 초기화 ---
// 페이지가 처음 로드될 때 진료과 드롭다운을 채웁니다.
populateDepartmentSelect();