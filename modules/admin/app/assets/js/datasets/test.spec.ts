

import { mount } from '@vue/test-utils';

const MyComponent = {
  data() {
    return {
      hello: "world"
    }
  },
  template: `<div>{{ hello }}</div>`
}

describe('Mounted App', () => {
  const wrapper = mount(MyComponent);

  test('is a Vue instance', () => {
    expect(wrapper.isVisible()).toBeTruthy()
    expect(wrapper.html()).toContain("world")
  })
})
