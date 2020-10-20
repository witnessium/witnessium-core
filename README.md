# MyGarage API

### `GET` **/vehicle/{vin}** 차량 트랜잭션 조회

### `GET` **/part/{part-no}** 부품 트랜잭션 조회

### `POST` **/transaction** 트랜잭션 등록
#### 예) 차량 트랜잭션

```json
{
  "signature": {
    "v": 27,
    "r": "00",
    "s": "00"
  },
  "value": {
    "networkId": 12,
    "inputs": [], // 송금을 위한 항목. 여기서는 비워둠
    "outputs": [], // 송금을 위한 항목. 여기서는 비워둠
    "data": {
      "vin": "",
      "carNo": "",
      "manufacturer": "",
      "model": "",
      "owner": "",
    }
  }
}
```

#### 예) 부품 트랜잭션


```json
{
  "signature": {
    "v": 27,
    "r": "00",
    "s": "00"
  },
  "value": {
    "networkId": 12,
    "inputs": [], // 송금을 위한 항목. 여기서는 비워둠
    "outputs": [], // 송금을 위한 항목. 여기서는 비워둠
    "data": {
      "name": "",
      "partNo": "",
      "manufacturer": "",
      "date": "",
      "warrenty": "",
      "supplier": "",
      "importer": "",
      "seller": "",
      "holder": "",
      "updatedAt": "",
    }
  }
}
```

## Javascript Library

웹페이지에서 다음 두 스크립트를 부른 다음 사용한다
"/resource/js/witnessium-core-js-jsdeps.min.js"
"/resource/js/witnessium-core-js-fastopt.js"

MyGarage.vehicle(), MyGarage.part() 두 메소드를 지원한다.

예)

```javascript
MyGarage.vehicle({vin: "KMHDL41BP8A000001", carNo: "23사5678", manufacturer: "Hyundai", model: "Sonata",  owner: "Alice"})
```

